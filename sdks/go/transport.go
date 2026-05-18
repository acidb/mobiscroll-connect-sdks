package mobiscroll

import (
	"bytes"
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"sync/atomic"

	"golang.org/x/sync/singleflight"
)

// apiClient is the internal HTTP layer shared by all resources. It injects
// Bearer auth, intercepts 401 responses to refresh + retry once, deduplicates
// concurrent refreshes via singleflight, and maps non-2xx responses to typed
// SDK errors.
type apiClient struct {
	cfg       *config
	http      *http.Client // base client; transport is the user's (or default), no bearer injection here
	tokenHTTP *http.Client // side-channel client for token-exchange/refresh; bypasses the 401 retry loop
	creds     atomic.Pointer[TokenResponse]
	refresh   singleflight.Group
	onRefresh atomic.Pointer[func(*TokenResponse)] // overrides config callback when set
}

func newAPIClient(cfg *config) *apiClient {
	httpClient := cfg.httpClient
	if httpClient == nil {
		httpClient = &http.Client{Timeout: cfg.timeout}
	} else if httpClient.Timeout == 0 {
		// honour caller's transport but apply our default timeout
		clone := *httpClient
		clone.Timeout = cfg.timeout
		httpClient = &clone
	}
	// tokenHTTP shares the same transport but has no bearer wrapping.
	tokenHTTP := &http.Client{Timeout: cfg.timeout, Transport: httpClient.Transport}
	return &apiClient{cfg: cfg, http: httpClient, tokenHTTP: tokenHTTP}
}

func (a *apiClient) setCredentials(t *TokenResponse) { a.creds.Store(t) }

func (a *apiClient) getCredentials() *TokenResponse { return a.creds.Load() }

func (a *apiClient) setOnTokensRefreshed(cb func(*TokenResponse)) {
	if cb == nil {
		a.onRefresh.Store(nil)
		return
	}
	a.onRefresh.Store(&cb)
}

// do performs an authenticated request: injects Bearer, retries once on 401
// after a deduplicated token refresh, and maps non-2xx responses to typed
// errors. out, if non-nil, receives the JSON-decoded response body.
func (a *apiClient) do(ctx context.Context, method, path, query string, body any, out any) error {
	resp, err := a.execute(ctx, method, path, query, body, true)
	if err != nil {
		return err
	}
	defer func() { _ = resp.Body.Close() }()
	if !isSuccess(resp.StatusCode) {
		return mapResponseError(resp)
	}
	if out == nil || resp.StatusCode == http.StatusNoContent {
		_, _ = io.Copy(io.Discard, resp.Body)
		return nil
	}
	if err := json.NewDecoder(resp.Body).Decode(out); err != nil && !errors.Is(err, io.EOF) {
		return fmt.Errorf("decode response: %w", err)
	}
	return nil
}

// execute builds and dispatches a single HTTP request. When allowRetry is
// true and the response is 401, it triggers a deduplicated refresh and retries
// the request once with the new bearer.
func (a *apiClient) execute(ctx context.Context, method, path, query string, body any, allowRetry bool) (*http.Response, error) {
	req, err := a.buildRequest(ctx, method, path, query, body)
	if err != nil {
		return nil, err
	}
	resp, err := a.http.Do(req)
	if err != nil {
		return nil, wrapNetwork(err)
	}
	if resp.StatusCode != http.StatusUnauthorized || !allowRetry {
		return resp, nil
	}
	// 401: try refresh + retry once.
	creds := a.creds.Load()
	if creds == nil || creds.RefreshToken == "" {
		return resp, nil // caller will map to AuthenticationError
	}
	_ = resp.Body.Close()
	if _, rerr := a.refreshTokens(ctx); rerr != nil {
		// Refresh failed → surface AuthenticationError so the consumer sees a
		// stable error type, regardless of whether the failure was 4xx or 5xx
		// from the token endpoint.
		var ae *AuthenticationError
		if errors.As(rerr, &ae) {
			return nil, rerr
		}
		return nil, &AuthenticationError{Message: "token refresh failed: " + rerr.Error()}
	}
	retryReq, err := a.buildRequest(ctx, method, path, query, body)
	if err != nil {
		return nil, err
	}
	retryResp, err := a.http.Do(retryReq)
	if err != nil {
		return nil, wrapNetwork(err)
	}
	return retryResp, nil
}

func (a *apiClient) buildRequest(ctx context.Context, method, path, query string, body any) (*http.Request, error) {
	u, err := a.buildURL(path, query)
	if err != nil {
		return nil, err
	}
	var rdr io.Reader
	if body != nil {
		buf, err := json.Marshal(body)
		if err != nil {
			return nil, fmt.Errorf("marshal request body: %w", err)
		}
		rdr = bytes.NewReader(buf)
	}
	req, err := http.NewRequestWithContext(ctx, method, u, rdr)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Accept", "application/json")
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	if c := a.creds.Load(); c != nil && c.AccessToken != "" {
		req.Header.Set("Authorization", "Bearer "+c.AccessToken)
	}
	return req, nil
}

func (a *apiClient) buildURL(path, query string) (string, error) {
	base := strings.TrimRight(a.cfg.baseURL, "/")
	if !strings.HasPrefix(path, "/") {
		path = "/" + path
	}
	full := base + path
	if query != "" {
		full += "?" + query
	}
	if _, err := url.Parse(full); err != nil {
		return "", fmt.Errorf("invalid url %q: %w", full, err)
	}
	return full, nil
}

// refreshTokens runs the OAuth refresh flow through a singleflight gate so
// concurrent 401s share a single network call.
func (a *apiClient) refreshTokens(ctx context.Context) (*TokenResponse, error) {
	result, err, _ := a.refresh.Do("refresh", func() (any, error) {
		return a.doRefresh(ctx)
	})
	if err != nil {
		return nil, err
	}
	return result.(*TokenResponse), nil
}

func (a *apiClient) doRefresh(ctx context.Context) (*TokenResponse, error) {
	old := a.creds.Load()
	if old == nil || old.RefreshToken == "" {
		return nil, &AuthenticationError{Message: "no refresh token available"}
	}
	form := url.Values{}
	form.Set("grant_type", "refresh_token")
	form.Set("refresh_token", old.RefreshToken)
	form.Set("redirect_uri", a.cfg.redirectURI)

	incoming, err := a.postForm(ctx, "/oauth/token", form)
	if err != nil {
		return nil, err
	}
	merged := old.mergedWith(incoming)
	a.creds.Store(merged)

	if cbPtr := a.onRefresh.Load(); cbPtr != nil && *cbPtr != nil {
		safeCall(*cbPtr, merged)
	} else if a.cfg.onTokensRefreshed != nil {
		safeCall(a.cfg.onTokensRefreshed, merged)
	}
	return merged, nil
}

// postForm sends an application/x-www-form-urlencoded body to a token endpoint
// using Basic auth + CLIENT_ID. It bypasses the 401 retry loop.
func (a *apiClient) postForm(ctx context.Context, path string, form url.Values) (*TokenResponse, error) {
	u, err := a.buildURL(path, "")
	if err != nil {
		return nil, err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, u, strings.NewReader(form.Encode()))
	if err != nil {
		return nil, err
	}
	creds := base64.StdEncoding.EncodeToString([]byte(a.cfg.clientID + ":" + a.cfg.clientSecret))
	req.Header.Set("Authorization", "Basic "+creds)
	req.Header.Set("CLIENT_ID", a.cfg.clientID)
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	req.Header.Set("Accept", "application/json")

	resp, err := a.tokenHTTP.Do(req)
	if err != nil {
		return nil, wrapNetwork(err)
	}
	defer func() { _ = resp.Body.Close() }()
	if !isSuccess(resp.StatusCode) {
		return nil, mapResponseError(resp)
	}
	var tok TokenResponse
	if err := json.NewDecoder(resp.Body).Decode(&tok); err != nil {
		return nil, fmt.Errorf("decode token response: %w", err)
	}
	return &tok, nil
}

func isSuccess(code int) bool { return code >= 200 && code < 300 }

// safeCall runs cb and swallows panics; refresh callbacks must never break
// the refresh path.
func safeCall(cb func(*TokenResponse), t *TokenResponse) {
	defer func() { _ = recover() }()
	cb(t)
}
