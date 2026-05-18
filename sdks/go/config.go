package mobiscroll

import (
	"net/http"
	"time"
)

const (
	defaultBaseURL = "https://connect.mobiscroll.com/api"
	defaultTimeout = 30 * time.Second
)

// config holds resolved client configuration. It is built by NewClient from
// the required positional arguments plus any ClientOption values.
type config struct {
	clientID          string
	clientSecret      string
	redirectURI       string
	baseURL           string
	timeout           time.Duration
	httpClient        *http.Client
	onTokensRefreshed func(*TokenResponse)
}

// ClientOption mutates a config during NewClient.
type ClientOption func(*config)

// WithBaseURL overrides the API base URL. Useful for staging or local mock
// servers. The URL must NOT have a trailing slash.
func WithBaseURL(u string) ClientOption {
	return func(c *config) { c.baseURL = u }
}

// WithHTTPClient injects a pre-built *http.Client. The SDK will install its
// own RoundTripper on top of whatever transport the provided client uses, so
// pass in a client only when you need to customize timeouts, proxies, or
// connection pooling — not when you want to wrap the transport yourself.
func WithHTTPClient(client *http.Client) ClientOption {
	return func(c *config) { c.httpClient = client }
}

// WithTimeout sets the per-request timeout (applies to the underlying
// http.Client). Default is 30 seconds.
func WithTimeout(d time.Duration) ClientOption {
	return func(c *config) { c.timeout = d }
}

// WithTokensRefreshedCallback registers a callback invoked after every
// successful token refresh. Use it to persist the new tokens (e.g. write to
// a cookie or database). The callback runs synchronously on the refreshing
// goroutine; keep it fast and panic-free.
func WithTokensRefreshedCallback(cb func(*TokenResponse)) ClientOption {
	return func(c *config) { c.onTokensRefreshed = cb }
}
