package mobiscroll

import (
	"context"
	"crypto/ed25519"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/textproto"
	"net/url"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"
)

const (
	webhookDefaultTolerance  = 300 * time.Second
	webhookKeysPath          = "/.well-known/webhook-keys"
	webhookPublicKeyPrefix   = "whpk_"
	webhookSignatureVersion  = "v1a"
	webhookKeysDefaultMaxAge = time.Hour
	webhookKeysMaxMaxAge     = 24 * time.Hour
	webhookKeysMinRefetch    = time.Minute
	webhookKeysFetchTimeout  = 10 * time.Second
	webhookKeysMaxBodyBytes  = 1 << 20
)

var (
	webhookTimestampPattern = regexp.MustCompile(`^\d+$`)
	webhookMaxAgePattern    = regexp.MustCompile(`(?i)max-age=(\d+)`)
)

// webhookNow is the clock behind timestamp checks and the key cache; tests replace it.
var webhookNow = time.Now

// VerifyWebhookOption customizes VerifyWebhookSignature.
type VerifyWebhookOption func(*verifyWebhookOptions)

type verifyWebhookOptions struct {
	tolerance time.Duration
	now       time.Time
}

// WithWebhookTolerance sets how far webhook-timestamp may be from the current
// time, in either direction. Default 5 minutes.
func WithWebhookTolerance(d time.Duration) VerifyWebhookOption {
	return func(o *verifyWebhookOptions) { o.tolerance = d }
}

// WithWebhookNow sets the time webhook-timestamp is checked against instead of
// the system clock. Useful in tests.
func WithWebhookNow(t time.Time) VerifyWebhookOption {
	return func(o *verifyWebhookOptions) { o.now = t }
}

// VerifyWebhookSignature verifies the Standard Webhooks v1a (Ed25519) signature
// of a Mobiscroll Connect delivery against the given public keys, without
// fetching anything. It returns nil when the delivery is genuine, or a
// *WebhookVerificationError saying why it is not.
//
// payload is the raw request body, exactly as received. headers must include
// webhook-id, webhook-timestamp and webhook-signature; names are matched
// case-insensitively, so a hand-built map with lowercase keys works too. The
// delivery is genuine if any v1a signature verifies against any of the "whpk_"
// publicKeys.
//
// Use it with a pinned key in handlers that cannot make outbound requests.
// Otherwise prefer Webhooks.VerifyWebhook, which fetches and refreshes the keys
// for you.
//
//	body, _ := io.ReadAll(r.Body)
//	err := mobiscroll.VerifyWebhookSignature(body, r.Header, []string{os.Getenv("MOBISCROLL_WEBHOOK_PUBLIC_KEY")})
func VerifyWebhookSignature(payload []byte, headers http.Header, publicKeys []string, opts ...VerifyWebhookOption) error {
	o := verifyWebhookOptions{tolerance: webhookDefaultTolerance}
	for _, opt := range opts {
		opt(&o)
	}
	if o.now.IsZero() {
		o.now = webhookNow()
	}

	id := webhookHeader(headers, "webhook-id")
	timestamp := webhookHeader(headers, "webhook-timestamp")
	signatureHeader := webhookHeader(headers, "webhook-signature")
	if id == "" || timestamp == "" || signatureHeader == "" {
		return &WebhookVerificationError{
			Message: "missing webhook-id, webhook-timestamp or webhook-signature header",
			Reason:  WebhookMissingHeaders,
		}
	}

	if !webhookTimestampPattern.MatchString(timestamp) {
		return &WebhookVerificationError{Message: "invalid webhook-timestamp header", Reason: WebhookInvalidTimestamp}
	}
	sent, err := strconv.ParseInt(timestamp, 10, 64)
	diff := o.now.Unix() - sent
	if diff < 0 {
		diff = -diff
	}
	if err != nil || diff > int64(o.tolerance/time.Second) {
		return &WebhookVerificationError{
			Message: "webhook timestamp is outside the allowed tolerance",
			Reason:  WebhookTimestampOutOfTolerance,
		}
	}

	keys := make([]ed25519.PublicKey, 0, len(publicKeys))
	for _, value := range publicKeys {
		if key, ok := parseWebhookPublicKey(value); ok {
			keys = append(keys, key)
		}
	}
	if len(keys) == 0 {
		return &WebhookVerificationError{Message: "no valid webhook public keys available", Reason: WebhookNoPublicKeys}
	}

	signedContent := make([]byte, 0, len(id)+len(timestamp)+2+len(payload))
	signedContent = append(signedContent, id+"."+timestamp+"."...)
	signedContent = append(signedContent, payload...)

	for _, entry := range strings.Split(signatureHeader, " ") {
		version, encoded, found := strings.Cut(entry, ",")
		if !found || version != webhookSignatureVersion {
			continue
		}
		signature, ok := decodeWebhookBase64(encoded)
		if !ok || len(signature) != ed25519.SignatureSize {
			continue
		}
		for _, key := range keys {
			if ed25519.Verify(key, signedContent, signature) {
				return nil
			}
		}
	}
	return &WebhookVerificationError{Message: "no webhook signature matched", Reason: WebhookNoMatchingSignature}
}

// webhookHeader returns the first value of the named header. http.Header
// canonicalizes keys only when written through its methods, so a map literal
// with lowercase keys is matched by a case-insensitive scan.
func webhookHeader(headers http.Header, name string) string {
	if values := headers[textproto.CanonicalMIMEHeaderKey(name)]; len(values) > 0 {
		return values[0]
	}
	for key, values := range headers {
		if strings.EqualFold(key, name) && len(values) > 0 {
			return values[0]
		}
	}
	return ""
}

func parseWebhookPublicKey(value string) (ed25519.PublicKey, bool) {
	raw, ok := decodeWebhookBase64(strings.TrimPrefix(strings.TrimSpace(value), webhookPublicKeyPrefix))
	if !ok || len(raw) != ed25519.PublicKeySize {
		return nil, false
	}
	return ed25519.PublicKey(raw), true
}

// decodeWebhookBase64 accepts standard base64 with or without padding.
func decodeWebhookBase64(s string) ([]byte, bool) {
	if raw, err := base64.StdEncoding.DecodeString(s); err == nil {
		return raw, true
	}
	if raw, err := base64.RawStdEncoding.DecodeString(s); err == nil {
		return raw, true
	}
	return nil, false
}

// webhookKeysURL derives the keys endpoint from the origin of the API base URL.
func webhookKeysURL(baseURL string) (string, error) {
	u, err := url.Parse(baseURL)
	if err != nil || u.Scheme == "" || u.Host == "" {
		return "", fmt.Errorf("mobiscroll: cannot derive the webhook keys URL from base URL %q", baseURL)
	}
	return u.Scheme + "://" + u.Host + webhookKeysPath, nil
}

// webhookKeyStore is the process-wide cache of the keys published at one keys
// URL, shared by every Client. It follows the endpoint's Cache-Control,
// fetches at most once a minute, and keeps the last good keys when a fetch
// fails.
type webhookKeyStore struct {
	url string

	mu            sync.Mutex
	keys          []string
	fetchedAt     time.Time
	lastAttemptAt time.Time
	maxAge        time.Duration
	inFlight      chan struct{} // closed when the running fetch completes; nil when idle
}

var webhookKeyStores = struct {
	sync.Mutex
	byURL map[string]*webhookKeyStore
}{byURL: map[string]*webhookKeyStore{}}

func webhookKeyStoreFor(keysURL string) *webhookKeyStore {
	webhookKeyStores.Lock()
	defer webhookKeyStores.Unlock()
	store, ok := webhookKeyStores.byURL[keysURL]
	if !ok {
		store = &webhookKeyStore{url: keysURL, maxAge: webhookKeysDefaultMaxAge}
		webhookKeyStores.byURL[keysURL] = store
	}
	return store
}

// resetWebhookKeyStores clears every cached key set; for tests.
func resetWebhookKeyStores() {
	webhookKeyStores.Lock()
	defer webhookKeyStores.Unlock()
	webhookKeyStores.byURL = map[string]*webhookKeyStore{}
}

// getKeys returns the cached keys, fetching them first when they are stale and
// the once-a-minute limit allows. A caller arriving during a fetch waits for it.
func (s *webhookKeyStore) getKeys(ctx context.Context, client *http.Client) ([]string, error) {
	s.mu.Lock()
	done := s.inFlight
	if done == nil {
		now := webhookNow()
		if now.Sub(s.fetchedAt) > s.maxAge && s.canRefetchLocked(now) {
			done = s.startFetchLocked(ctx, client)
		}
	}
	s.mu.Unlock()

	if done != nil {
		if err := waitForFetch(ctx, done); err != nil {
			return nil, err
		}
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.keys, nil
}

// refresh fetches the keys now, or joins the fetch already running.
func (s *webhookKeyStore) refresh(ctx context.Context, client *http.Client) error {
	s.mu.Lock()
	done := s.inFlight
	if done == nil {
		done = s.startFetchLocked(ctx, client)
	}
	s.mu.Unlock()
	return waitForFetch(ctx, done)
}

func (s *webhookKeyStore) canRefetch() bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.canRefetchLocked(webhookNow())
}

func (s *webhookKeyStore) canRefetchLocked(now time.Time) bool {
	return now.Sub(s.lastAttemptAt) >= webhookKeysMinRefetch
}

func (s *webhookKeyStore) startFetchLocked(ctx context.Context, client *http.Client) chan struct{} {
	done := make(chan struct{})
	s.inFlight = done
	s.lastAttemptAt = webhookNow()
	// Detached from the caller's cancellation: other callers may be waiting on
	// this fetch. The client's timeout still bounds it.
	fetchCtx := context.WithoutCancel(ctx)
	go func() {
		defer close(done)
		keys, maxAge, err := fetchWebhookKeys(fetchCtx, client, s.url)
		s.mu.Lock()
		defer s.mu.Unlock()
		s.inFlight = nil
		if err == nil {
			s.keys = keys
			s.fetchedAt = webhookNow()
			s.maxAge = maxAge
		}
	}()
	return done
}

func waitForFetch(ctx context.Context, done <-chan struct{}) error {
	select {
	case <-done:
		return nil
	case <-ctx.Done():
		return ctx.Err()
	}
}

// fetchWebhookKeys loads the usable "whpk_" Ed25519 keys and their max-age with
// a plain, unauthenticated GET. It fails when none are usable.
func fetchWebhookKeys(ctx context.Context, client *http.Client, keysURL string) ([]string, time.Duration, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, keysURL, nil)
	if err != nil {
		return nil, 0, err
	}
	req.Header.Set("Accept", "application/json")
	resp, err := client.Do(req)
	if err != nil {
		return nil, 0, err
	}
	defer func() { _ = resp.Body.Close() }()
	if !isSuccess(resp.StatusCode) {
		return nil, 0, fmt.Errorf("webhook keys endpoint returned HTTP %d", resp.StatusCode)
	}

	var body struct {
		Keys []json.RawMessage `json:"keys"`
	}
	if err := json.NewDecoder(io.LimitReader(resp.Body, webhookKeysMaxBodyBytes)).Decode(&body); err != nil {
		return nil, 0, err
	}
	var keys []string
	for _, raw := range body.Keys {
		var entry struct {
			Alg string `json:"alg"`
			Key string `json:"key"`
		}
		if json.Unmarshal(raw, &entry) != nil || !strings.HasPrefix(entry.Key, webhookPublicKeyPrefix) {
			continue
		}
		if entry.Alg == "" || strings.EqualFold(entry.Alg, "ed25519") {
			keys = append(keys, entry.Key)
		}
	}
	if len(keys) == 0 {
		return nil, 0, errors.New("webhook keys endpoint returned no usable keys")
	}
	return keys, parseWebhookKeysMaxAge(resp.Header.Get("Cache-Control")), nil
}

func parseWebhookKeysMaxAge(cacheControl string) time.Duration {
	match := webhookMaxAgePattern.FindStringSubmatch(cacheControl)
	if match == nil {
		return webhookKeysDefaultMaxAge
	}
	seconds, err := strconv.ParseInt(match[1], 10, 64)
	if err != nil || seconds > int64(webhookKeysMaxMaxAge/time.Second) {
		return webhookKeysMaxMaxAge
	}
	return time.Duration(seconds) * time.Second
}
