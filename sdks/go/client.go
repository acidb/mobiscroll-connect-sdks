// Package mobiscroll is the Go SDK for the Mobiscroll Connect API. It mirrors
// the public surface of the Node/Python/PHP/.NET/Java SDKs, adjusted for Go
// conventions.
//
// Quick start:
//
//	client := mobiscroll.NewClient(clientID, clientSecret, redirectURI)
//	url := client.Auth().GenerateAuthURL(&mobiscroll.AuthURLParams{UserID: "u1"})
//	// redirect the user to url; in your callback handler:
//	tokens, err := client.Auth().GetToken(ctx, code)
//	cals, err := client.Calendars().List(ctx)
package mobiscroll

// Client is the top-level SDK entry point. Construct one with NewClient and
// reuse it across requests; the underlying HTTP client and refresh state are
// safe for concurrent use.
type Client struct {
	api       *apiClient
	auth      *authService
	calendars *calendarsService
	events    *eventsService
}

// NewClient builds a Client bound to the given OAuth credentials.
//
//	client := mobiscroll.NewClient(
//	    "client-id",
//	    "client-secret",
//	    "https://app.example.com/oauth/callback",
//	    mobiscroll.WithTimeout(15*time.Second),
//	)
func NewClient(clientID, clientSecret, redirectURI string, opts ...ClientOption) *Client {
	cfg := &config{
		clientID:     clientID,
		clientSecret: clientSecret,
		redirectURI:  redirectURI,
		baseURL:      defaultBaseURL,
		timeout:      defaultTimeout,
	}
	for _, opt := range opts {
		opt(cfg)
	}
	api := newAPIClient(cfg)
	c := &Client{api: api}
	c.auth = &authService{api: api}
	c.calendars = &calendarsService{api: api}
	c.events = &eventsService{api: api}
	return c
}

// Auth returns the OAuth + connection-management resource.
func (c *Client) Auth() *authService { return c.auth }

// Calendars returns the calendars resource.
func (c *Client) Calendars() *calendarsService { return c.calendars }

// Events returns the events resource.
func (c *Client) Events() *eventsService { return c.events }

// SetCredentials stores a token pair the SDK will use on subsequent requests.
// Typically called after Auth.GetToken or when restoring credentials from
// persistent storage.
func (c *Client) SetCredentials(tokens *TokenResponse) { c.api.setCredentials(tokens) }

// Credentials returns the currently stored credentials, or nil if none.
func (c *Client) Credentials() *TokenResponse { return c.api.getCredentials() }

// OnTokensRefreshed registers a callback invoked after every successful token
// refresh. Pass nil to clear. This overrides any callback supplied via
// WithTokensRefreshedCallback. Useful in web apps where each request needs to
// write the refreshed tokens back to a cookie or session store.
func (c *Client) OnTokensRefreshed(cb func(*TokenResponse)) { c.api.setOnTokensRefreshed(cb) }
