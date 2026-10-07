package mobiscroll_test

import (
	"context"
	"crypto/ed25519"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"os"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	mobiscroll "github.com/acidb/mobiscroll-connect-sdks/sdks/go"
	"github.com/acidb/mobiscroll-connect-sdks/sdks/go/testsupport"
)

type webhookVector struct {
	Name       string            `json:"name"`
	Valid      bool              `json:"valid"`
	Headers    map[string]string `json:"headers"`
	Body       string            `json:"body"`
	PublicKeys []string          `json:"publicKeys"`
	Now        int64             `json:"now"`
}

type webhookVectors struct {
	Keys struct {
		Active    string `json:"active"`
		Previous  string `json:"previous"`
		Unrelated string `json:"unrelated"`
	} `json:"keys"`
	Cases []webhookVector `json:"cases"`
}

func loadWebhookVectors(t *testing.T) webhookVectors {
	t.Helper()
	raw, err := os.ReadFile("testdata/webhook-vectors.json")
	if err != nil {
		t.Fatalf("read vectors: %v", err)
	}
	var v webhookVectors
	if err := json.Unmarshal(raw, &v); err != nil {
		t.Fatalf("parse vectors: %v", err)
	}
	return v
}

func (v webhookVectors) byName(t *testing.T, name string) webhookVector {
	t.Helper()
	for _, c := range v.Cases {
		if c.Name == name {
			return c
		}
	}
	t.Fatalf("missing vector %q", name)
	return webhookVector{}
}

// header builds an http.Header the way a caller would by hand: a map literal
// with the lowercase names, bypassing canonicalization.
func (c webhookVector) header() http.Header {
	h := http.Header{}
	for k, v := range c.Headers {
		h[k] = []string{v}
	}
	return h
}

func requireReason(t *testing.T, err error, want mobiscroll.WebhookVerificationReason) {
	t.Helper()
	var ve *mobiscroll.WebhookVerificationError
	if !errors.As(err, &ve) {
		t.Fatalf("expected *WebhookVerificationError, got %T: %v", err, err)
	}
	if ve.Reason != want {
		t.Fatalf("expected reason %q, got %q (%v)", want, ve.Reason, err)
	}
}

func TestVerifyWebhookSignature_Vectors(t *testing.T) {
	vectors := loadWebhookVectors(t)
	for _, c := range vectors.Cases {
		t.Run(c.Name, func(t *testing.T) {
			err := mobiscroll.VerifyWebhookSignature([]byte(c.Body), c.header(), c.PublicKeys,
				mobiscroll.WithWebhookNow(time.Unix(c.Now, 0)))
			if c.Valid && err != nil {
				t.Fatalf("expected valid, got %v", err)
			}
			if !c.Valid {
				var ve *mobiscroll.WebhookVerificationError
				if !errors.As(err, &ve) {
					t.Fatalf("expected *WebhookVerificationError, got %T: %v", err, err)
				}
			}
		})
	}
}

func TestVerifyWebhookSignature_HeaderNamesAnyCase(t *testing.T) {
	vectors := loadWebhookVectors(t)
	c := vectors.byName(t, "valid single signature")
	now := mobiscroll.WithWebhookNow(time.Unix(c.Now, 0))

	canonical := http.Header{}
	upper := http.Header{}
	for k, v := range c.Headers {
		canonical.Set(k, v)
		canonical.Add(k, "ignored second value")
		upper[strings.ToUpper(k)] = []string{v}
	}
	for name, h := range map[string]http.Header{"canonical": canonical, "upper": upper, "lower": c.header()} {
		if err := mobiscroll.VerifyWebhookSignature([]byte(c.Body), h, c.PublicKeys, now); err != nil {
			t.Errorf("%s: %v", name, err)
		}
	}
}

func TestVerifyWebhookSignature_Reasons(t *testing.T) {
	vectors := loadWebhookVectors(t)
	tests := []struct {
		name string
		want mobiscroll.WebhookVerificationReason
	}{
		{"missing webhook-id", mobiscroll.WebhookMissingHeaders},
		{"non-numeric timestamp", mobiscroll.WebhookInvalidTimestamp},
		{"timestamp 301 s old, stale", mobiscroll.WebhookTimestampOutOfTolerance},
		{"timestamp 301 s in the future", mobiscroll.WebhookTimestampOutOfTolerance},
		{"no public keys", mobiscroll.WebhookNoPublicKeys},
		{"tampered body", mobiscroll.WebhookNoMatchingSignature},
		{"malformed base64 signature", mobiscroll.WebhookNoMatchingSignature},
		{"only a v1 HMAC entry", mobiscroll.WebhookNoMatchingSignature},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			c := vectors.byName(t, tt.name)
			err := mobiscroll.VerifyWebhookSignature([]byte(c.Body), c.header(), c.PublicKeys,
				mobiscroll.WithWebhookNow(time.Unix(c.Now, 0)))
			requireReason(t, err, tt.want)
		})
	}
}

func TestVerifyWebhookSignature_ErrorShape(t *testing.T) {
	vectors := loadWebhookVectors(t)
	c := vectors.byName(t, "timestamp 301 s old, stale")
	err := mobiscroll.VerifyWebhookSignature([]byte(c.Body), c.header(), c.PublicKeys,
		mobiscroll.WithWebhookNow(time.Unix(c.Now, 0)))

	var me mobiscroll.MobiscrollError
	if !errors.As(fmt.Errorf("wrapped: %w", err), &me) || me.Code() != "WEBHOOK_VERIFICATION_ERROR" {
		t.Fatalf("expected a MobiscrollError with code WEBHOOK_VERIFICATION_ERROR, got %T: %v", err, err)
	}
	requireReason(t, fmt.Errorf("wrapped: %w", err), mobiscroll.WebhookTimestampOutOfTolerance)

	err = mobiscroll.VerifyWebhookSignature([]byte(c.Body), c.header(), c.PublicKeys,
		mobiscroll.WithWebhookNow(time.Unix(c.Now, 0)), mobiscroll.WithWebhookTolerance(301*time.Second))
	if err != nil {
		t.Fatalf("expected the wider tolerance to accept it, got %v", err)
	}
}

// --- Webhooks.VerifyWebhook ---------------------------------------------------

type webhookFixture struct {
	vectors webhookVectors
	valid   webhookVector
	srv     *testsupport.MockServer
	clock   atomic.Int64 // Unix seconds
}

func newWebhookFixture(t *testing.T) *webhookFixture {
	t.Helper()
	mobiscroll.ResetWebhookKeyStores()
	t.Cleanup(mobiscroll.ResetWebhookKeyStores)
	f := &webhookFixture{vectors: loadWebhookVectors(t), srv: testsupport.NewMockServer(t)}
	f.valid = f.vectors.byName(t, "valid single signature")
	f.clock.Store(f.valid.Now)
	t.Cleanup(mobiscroll.SetWebhookClock(func() time.Time { return time.Unix(f.clock.Load(), 0) }))
	return f
}

func (f *webhookFixture) client(opts ...mobiscroll.ClientOption) *mobiscroll.Client {
	opts = append([]mobiscroll.ClientOption{mobiscroll.WithBaseURL(f.srv.URL + "/api")}, opts...)
	return mobiscroll.NewClient("id", "secret", "https://app/cb", opts...)
}

func (f *webhookFixture) advance(seconds int64) { f.clock.Add(seconds) }

func keysResponse(cacheControl string, keys ...string) testsupport.MockResponse {
	entries := make([]map[string]string, len(keys))
	for i, key := range keys {
		entries[i] = map[string]string{"id": strconv.Itoa(i), "alg": "ed25519", "key": key, "status": "active"}
	}
	body, _ := json.Marshal(map[string]any{"keys": entries})
	return testsupport.MockResponse{
		Body:    string(body),
		Headers: map[string]string{"Content-Type": "application/json", "Cache-Control": cacheControl},
	}
}

func (f *webhookFixture) verifyValid(c *mobiscroll.Client) (*mobiscroll.WebhookDelivery, error) {
	return c.Webhooks().VerifyWebhook(context.Background(), []byte(f.valid.Body), f.valid.header())
}

type testSigner struct {
	priv ed25519.PrivateKey
	key  string
}

func newTestSigner(t *testing.T) testSigner {
	t.Helper()
	pub, priv, err := ed25519.GenerateKey(nil)
	if err != nil {
		t.Fatal(err)
	}
	return testSigner{priv: priv, key: "whpk_" + base64.StdEncoding.EncodeToString(pub)}
}

func (s testSigner) sign(id string, timestamp int64, body string) http.Header {
	ts := strconv.FormatInt(timestamp, 10)
	sig := ed25519.Sign(s.priv, []byte(id+"."+ts+"."+body))
	return http.Header{
		"Webhook-Id":        {id},
		"Webhook-Timestamp": {ts},
		"Webhook-Signature": {"v1a," + base64.StdEncoding.EncodeToString(sig)},
	}
}

func TestVerifyWebhook_FetchesKeysLazilyFromTheOrigin(t *testing.T) {
	f := newWebhookFixture(t)
	f.srv.Enqueue(keysResponse("public, max-age=3600", f.vectors.Keys.Active))
	c := f.client()
	c.SetCredentials(&mobiscroll.TokenResponse{AccessToken: "at"})
	if n := f.srv.RequestCount(); n != 0 {
		t.Fatalf("expected no request before the first verification, got %d", n)
	}

	delivery, err := f.verifyValid(c)
	if err != nil {
		t.Fatalf("VerifyWebhook: %v", err)
	}
	if delivery.UserID != "user_42" || delivery.CalendarID != "primary" || delivery.Provider != mobiscroll.ProviderGoogle {
		t.Fatalf("unexpected delivery: %+v", delivery)
	}
	if len(delivery.Events) != 1 || delivery.Events[0].Title != "Café meeting ☕ — Zoë" || delivery.Events[0].ID != "evt_1" {
		t.Fatalf("unexpected events: %+v", delivery.Events)
	}

	req := f.srv.Requests()[0]
	if req.Method != http.MethodGet || req.Path != "/.well-known/webhook-keys" {
		t.Fatalf("unexpected key request: %s %s", req.Method, req.Path)
	}
	if got := req.Header.Get("Authorization"); got != "" {
		t.Errorf("key request must be unauthenticated, got Authorization %q", got)
	}
}

func TestVerifyWebhook_SharesTheCacheAcrossClientsAndHonoursMaxAge(t *testing.T) {
	f := newWebhookFixture(t)
	f.srv.Enqueue(keysResponse("max-age=120", f.vectors.Keys.Active))
	f.srv.Enqueue(keysResponse("max-age=120", f.vectors.Keys.Active))

	for i := 0; i < 2; i++ {
		if _, err := f.verifyValid(f.client()); err != nil {
			t.Fatalf("verify %d: %v", i, err)
		}
	}
	if n := f.srv.RequestCount(); n != 1 {
		t.Fatalf("expected 1 key fetch, got %d", n)
	}

	f.advance(121)
	if _, err := f.verifyValid(f.client()); err != nil {
		t.Fatalf("verify after max-age: %v", err)
	}
	if n := f.srv.RequestCount(); n != 2 {
		t.Fatalf("expected a re-fetch after max-age, got %d fetches", n)
	}
}

func TestVerifyWebhook_RefetchesOnceAfterARotation(t *testing.T) {
	f := newWebhookFixture(t)
	f.srv.Enqueue(keysResponse("max-age=3600", f.vectors.Keys.Unrelated))
	f.srv.Enqueue(keysResponse("max-age=3600", f.vectors.Keys.Active))

	f.advance(-61)
	_, err := f.verifyValid(f.client())
	requireReason(t, err, mobiscroll.WebhookNoMatchingSignature)
	f.advance(61)

	delivery, err := f.verifyValid(f.client())
	if err != nil {
		t.Fatalf("expected the re-fetched key to verify, got %v", err)
	}
	if delivery.CalendarID != "primary" {
		t.Fatalf("unexpected delivery: %+v", delivery)
	}
	if n := f.srv.RequestCount(); n != 2 {
		t.Fatalf("expected 2 key fetches, got %d", n)
	}
}

func TestVerifyWebhook_DoesNotRefetchMoreThanOnceAMinuteOnForgeries(t *testing.T) {
	f := newWebhookFixture(t)
	f.srv.Enqueue(keysResponse("max-age=3600", f.vectors.Keys.Active))
	forged := f.vectors.byName(t, "tampered body")
	c := f.client()

	for i := 0; i < 2; i++ {
		_, err := c.Webhooks().VerifyWebhook(context.Background(), []byte(forged.Body), forged.header())
		requireReason(t, err, mobiscroll.WebhookNoMatchingSignature)
	}
	if n := f.srv.RequestCount(); n != 1 {
		t.Fatalf("expected 1 key fetch, got %d", n)
	}
}

func TestVerifyWebhook_FallsBackToThePinnedKeyWhenTheFetchFails(t *testing.T) {
	f := newWebhookFixture(t)
	f.srv.EnqueueStatus(http.StatusServiceUnavailable)

	delivery, err := f.verifyValid(f.client(mobiscroll.WithWebhookPublicKey(f.vectors.Keys.Active)))
	if err != nil {
		t.Fatalf("expected the pinned key to verify, got %v", err)
	}
	if delivery.Provider != mobiscroll.ProviderGoogle {
		t.Fatalf("unexpected delivery: %+v", delivery)
	}
}

func TestVerifyWebhook_IgnoresThePinnedKeyWhileFetchedKeysExist(t *testing.T) {
	f := newWebhookFixture(t)
	f.srv.Enqueue(keysResponse("max-age=3600", f.vectors.Keys.Unrelated))

	_, err := f.verifyValid(f.client(mobiscroll.WithWebhookPublicKey(f.vectors.Keys.Active)))
	requireReason(t, err, mobiscroll.WebhookNoMatchingSignature)
}

func TestVerifyWebhook_KeepsTheLastGoodKeysWhenARefreshFails(t *testing.T) {
	f := newWebhookFixture(t)
	signer := newTestSigner(t)
	body := f.valid.Body
	f.srv.Enqueue(keysResponse("max-age=3600", signer.key))
	f.srv.EnqueueStatus(http.StatusInternalServerError)
	c := f.client()

	if _, err := c.Webhooks().VerifyWebhook(context.Background(), []byte(body), signer.sign("msg_1", f.clock.Load(), body)); err != nil {
		t.Fatalf("first verify: %v", err)
	}

	f.advance(3601)
	if _, err := c.Webhooks().VerifyWebhook(context.Background(), []byte(body), signer.sign("msg_2", f.clock.Load(), body)); err != nil {
		t.Fatalf("expected the cached keys to survive a failed refresh, got %v", err)
	}
	if n := f.srv.RequestCount(); n != 2 {
		t.Fatalf("expected 2 key fetches, got %d", n)
	}
}

func TestVerifyWebhook_NoPublicKeysWhenNothingIsAvailable(t *testing.T) {
	f := newWebhookFixture(t)
	f.srv.EnqueueStatus(http.StatusInternalServerError)

	_, err := f.verifyValid(f.client())
	requireReason(t, err, mobiscroll.WebhookNoPublicKeys)
	if n := f.srv.RequestCount(); n != 1 {
		t.Fatalf("expected 1 key fetch, got %d", n)
	}
}

func TestVerifyWebhook_IgnoresKeysOfOtherAlgorithms(t *testing.T) {
	f := newWebhookFixture(t)
	f.srv.EnqueueJSON(fmt.Sprintf(`{"keys":[{"alg":"RS256","key":%q},{"key":"not-a-whpk-key"},{"key":42}]}`, f.vectors.Keys.Active))

	_, err := f.verifyValid(f.client())
	requireReason(t, err, mobiscroll.WebhookNoPublicKeys)
}

func TestVerifyWebhook_ConcurrentCallsShareOneFetch(t *testing.T) {
	f := newWebhookFixture(t)
	release := make(chan struct{})
	active := keysResponse("max-age=3600", f.vectors.Keys.Active)
	f.srv.Dispatch(func(testsupport.RecordedRequest) testsupport.MockResponse {
		<-release
		return active
	})
	c := f.client()

	const callers = 10
	var wg sync.WaitGroup
	errs := make(chan error, callers)
	for i := 0; i < callers; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			_, err := f.verifyValid(c)
			errs <- err
		}()
	}
	time.Sleep(50 * time.Millisecond)
	close(release)
	wg.Wait()
	close(errs)

	for err := range errs {
		if err != nil {
			t.Errorf("caller failed: %v", err)
		}
	}
	if n := f.srv.RequestCount(); n != 1 {
		t.Fatalf("expected 1 key fetch, got %d", n)
	}
}

func TestVerifyWebhook_ReturnsWhenTheContextEnds(t *testing.T) {
	f := newWebhookFixture(t)
	release := make(chan struct{})
	t.Cleanup(func() { close(release) })
	f.srv.Dispatch(func(testsupport.RecordedRequest) testsupport.MockResponse {
		<-release
		return testsupport.MockResponse{Status: http.StatusServiceUnavailable}
	})

	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	_, err := f.client().Webhooks().VerifyWebhook(ctx, []byte(f.valid.Body), f.valid.header())
	if !errors.Is(err, context.Canceled) {
		t.Fatalf("expected context.Canceled, got %v", err)
	}
}

func TestVerifyWebhook_RejectsABodyThatIsNotJSON(t *testing.T) {
	f := newWebhookFixture(t)
	signer := newTestSigner(t)
	f.srv.Enqueue(keysResponse("max-age=3600", signer.key))

	body := "not json"
	_, err := f.client().Webhooks().VerifyWebhook(context.Background(), []byte(body), signer.sign("msg_1", f.clock.Load(), body))
	requireReason(t, err, mobiscroll.WebhookInvalidPayload)
}

func TestVerifyWebhook_ParsesTheFullDelivery(t *testing.T) {
	f := newWebhookFixture(t)
	signer := newTestSigner(t)
	f.srv.Enqueue(keysResponse("max-age=3600", signer.key))

	body := `{"provider":"microsoft","userId":"u1","calendarId":"cal","changeType":"mixed",` +
		`"events":[{"provider":"microsoft","id":"e1","calendarId":"cal","title":"T",` +
		`"start":"2026-10-07T09:00:00Z","end":"2026-10-07T09:30:00Z","changeType":"deleted","unknown":1}],` +
		`"timestamp":"2026-10-06T10:00:00.000Z","metadata":{"channelId":"ch","eventCount":1,"isInitialSync":true},"extra":true}`
	delivery, err := f.client().Webhooks().VerifyWebhook(context.Background(), []byte(body), signer.sign("msg_1", f.clock.Load(), body))
	if err != nil {
		t.Fatalf("VerifyWebhook: %v", err)
	}
	if delivery.ChangeType != "mixed" || delivery.Timestamp != "2026-10-06T10:00:00.000Z" {
		t.Fatalf("unexpected delivery: %+v", delivery)
	}
	if delivery.Metadata != (mobiscroll.WebhookDeliveryMetadata{ChannelID: "ch", EventCount: 1, IsInitialSync: true}) {
		t.Fatalf("unexpected metadata: %+v", delivery.Metadata)
	}
	ev := delivery.Events[0]
	if ev.ChangeType != "deleted" || ev.ID != "e1" || ev.Start == nil || ev.Start.Hour() != 9 {
		t.Fatalf("unexpected event: %+v", ev)
	}
}
