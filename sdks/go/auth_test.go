package mobiscroll_test

import (
	"context"
	"encoding/json"
	"errors"
	"strings"
	"testing"

	mobiscroll "github.com/acidb/mobiscroll-connect-sdks/sdks/go"
	"github.com/acidb/mobiscroll-connect-sdks/sdks/go/testsupport"
)

func TestGenerateAuthURL_IncludesAllParams(t *testing.T) {
	c := mobiscroll.NewClient("the-client", "secret", "https://app/cb",
		mobiscroll.WithBaseURL("https://example.test/api"),
	)
	url := c.Auth().GenerateAuthURL(&mobiscroll.AuthURLParams{
		UserID:    "user-1",
		State:     "xyz",
		Scope:     "calendars events",
		Providers: []mobiscroll.Provider{mobiscroll.ProviderGoogle, mobiscroll.ProviderMicrosoft},
		Lng:       "es",
		Theme:     "dark",
	})
	if !strings.HasPrefix(url, "https://example.test/api/oauth/authorize?") {
		t.Fatalf("unexpected URL prefix: %s", url)
	}
	for _, want := range []string{
		"response_type=code",
		"client_id=the-client",
		"redirect_uri=https%3A%2F%2Fapp%2Fcb",
		"user_id=user-1",
		"state=xyz",
		"scope=calendars+events",
		"providers=google",
		"providers=microsoft",
		"lng=es",
		"theme=dark",
	} {
		if !strings.Contains(url, want) {
			t.Errorf("URL missing %q: %s", want, url)
		}
	}
}

func TestGetToken_ExchangesCodeAndSetsCredentials(t *testing.T) {
	srv := testsupport.NewMockServer(t)
	srv.EnqueueJSON(`{"access_token":"a","token_type":"Bearer","expires_in":3600,"refresh_token":"r"}`)

	c := mobiscroll.NewClient("the-client", "secret", "https://app/cb",
		mobiscroll.WithBaseURL(srv.URL+"/api"),
	)
	tok, err := c.Auth().GetToken(context.Background(), "the-code")
	if err != nil {
		t.Fatalf("GetToken: %v", err)
	}
	if tok.AccessToken != "a" || tok.RefreshToken != "r" {
		t.Fatalf("unexpected tokens: %+v", tok)
	}
	if got := c.Credentials(); got == nil || got.AccessToken != "a" {
		t.Fatalf("credentials not stored: %+v", got)
	}

	reqs := srv.Requests()
	if len(reqs) != 1 {
		t.Fatalf("expected 1 request, got %d", len(reqs))
	}
	r := reqs[0]
	if r.Method != "POST" || r.Path != "/api/oauth/token" {
		t.Fatalf("unexpected request: %s %s", r.Method, r.Path)
	}
	if !strings.HasPrefix(r.Header.Get("Authorization"), "Basic ") {
		t.Errorf("expected Basic auth header, got %q", r.Header.Get("Authorization"))
	}
	if r.Header.Get("CLIENT_ID") != "the-client" {
		t.Errorf("expected CLIENT_ID header, got %q", r.Header.Get("CLIENT_ID"))
	}
	body := string(r.Body)
	for _, want := range []string{"grant_type=authorization_code", "code=the-code"} {
		if !strings.Contains(body, want) {
			t.Errorf("body missing %q: %s", want, body)
		}
	}
}

func TestDisconnect_FallsBackToLegacyPathOn404(t *testing.T) {
	srv := testsupport.NewMockServer(t)
	srv.Enqueue(testsupport.MockResponse{Status: 404, Body: `{"message":"not found"}`,
		Headers: map[string]string{"Content-Type": "application/json"}})
	srv.EnqueueJSON(`{"success":true}`)

	c := mobiscroll.NewClient("id", "secret", "https://app/cb",
		mobiscroll.WithBaseURL(srv.URL+"/api"),
	)
	c.SetCredentials(&mobiscroll.TokenResponse{AccessToken: "at", RefreshToken: "rt"})

	resp, err := c.Auth().Disconnect(context.Background(), &mobiscroll.DisconnectParams{
		Provider: mobiscroll.ProviderGoogle,
		Account:  "alice@example.com",
	})
	if err != nil {
		t.Fatalf("Disconnect: %v", err)
	}
	if !resp.Success {
		t.Fatalf("expected success=true")
	}

	reqs := srv.Requests()
	if len(reqs) != 2 {
		t.Fatalf("expected 2 requests, got %d", len(reqs))
	}
	if reqs[0].Method != "POST" || !strings.HasPrefix(reqs[0].Path, "/api/oauth/disconnect?") {
		t.Errorf("first request: %s %s", reqs[0].Method, reqs[0].Path)
	}
	if reqs[1].Method != "POST" || !strings.HasPrefix(reqs[1].Path, "/api/disconnect?") {
		t.Errorf("second request: %s %s", reqs[1].Method, reqs[1].Path)
	}
	for _, want := range []string{"provider=google", "account=alice%40example.com"} {
		if !strings.Contains(reqs[1].Path, want) {
			t.Errorf("legacy path missing %q: %s", want, reqs[1].Path)
		}
	}
}

func TestGetConnectionStatus_FallsBackToLegacyPathOn404(t *testing.T) {
	srv := testsupport.NewMockServer(t)
	srv.Enqueue(testsupport.MockResponse{Status: 404, Body: `{"message":"not found"}`,
		Headers: map[string]string{"Content-Type": "application/json"}})
	srv.EnqueueJSON(`{"connections":{"google":[{"id":"u@g.com"}]},"limitReached":false}`)

	c := mobiscroll.NewClient("id", "secret", "https://app/cb",
		mobiscroll.WithBaseURL(srv.URL+"/api"),
	)
	c.SetCredentials(&mobiscroll.TokenResponse{AccessToken: "at", RefreshToken: "rt"})

	status, err := c.Auth().GetConnectionStatus(context.Background())
	if err != nil {
		t.Fatalf("GetConnectionStatus: %v", err)
	}
	if status.LimitReached {
		t.Fatalf("expected limitReached=false")
	}
	if accs, ok := status.Connections[mobiscroll.ProviderGoogle]; !ok || len(accs) != 1 || accs[0].ID != "u@g.com" {
		t.Fatalf("unexpected connections: %+v", status.Connections)
	}
}

func TestGetConnectionStatus_ReportsCalendarPermission(t *testing.T) {
	srv := testsupport.NewMockServer(t)
	srv.EnqueueJSON(`{"connections":{"google":[` +
		`{"id":"granted@g.com","grantedScopes":["openid","https://www.googleapis.com/auth/calendar"],"calendarPermissionGranted":true},` +
		`{"id":"withheld@g.com","grantedScopes":["openid"],"calendarPermissionGranted":false}],` +
		`"apple":[{"id":"u@icloud.com","grantedScopes":[],"calendarPermissionGranted":null}]},"limitReached":false}`)

	c := mobiscroll.NewClient("id", "secret", "https://app/cb",
		mobiscroll.WithBaseURL(srv.URL+"/api"),
	)
	c.SetCredentials(&mobiscroll.TokenResponse{AccessToken: "at"})

	status, err := c.Auth().GetConnectionStatus(context.Background())
	if err != nil {
		t.Fatalf("GetConnectionStatus: %v", err)
	}

	google := status.Connections[mobiscroll.ProviderGoogle]
	if len(google) != 2 {
		t.Fatalf("expected 2 google accounts, got %d", len(google))
	}
	if google[0].CalendarPermissionGranted == nil || !*google[0].CalendarPermissionGranted {
		t.Errorf("expected granted@g.com to have calendar permission")
	}
	if google[1].CalendarPermissionGranted == nil || *google[1].CalendarPermissionGranted {
		t.Errorf("expected withheld@g.com to lack calendar permission")
	}
	if len(google[1].GrantedScopes) != 1 || google[1].GrantedScopes[0] != "openid" {
		t.Errorf("unexpected granted scopes: %v", google[1].GrantedScopes)
	}
	// Apple has no scopes to withhold, so the flag is nil rather than false.
	if apple := status.Connections[mobiscroll.ProviderApple]; len(apple) != 1 || apple[0].CalendarPermissionGranted != nil {
		t.Errorf("expected apple account with nil permission flag: %+v", apple)
	}
}

func TestGetConnectionStatus_DefaultsWhenScopeFieldsAbsent(t *testing.T) {
	srv := testsupport.NewMockServer(t)
	srv.EnqueueJSON(`{"connections":{"google":[{"id":"u@g.com"}]},"limitReached":false}`)

	c := mobiscroll.NewClient("id", "secret", "https://app/cb",
		mobiscroll.WithBaseURL(srv.URL+"/api"),
	)
	c.SetCredentials(&mobiscroll.TokenResponse{AccessToken: "at"})

	status, err := c.Auth().GetConnectionStatus(context.Background())
	if err != nil {
		t.Fatalf("GetConnectionStatus: %v", err)
	}
	acct := status.Connections[mobiscroll.ProviderGoogle][0]
	if len(acct.GrantedScopes) != 0 || acct.CalendarPermissionGranted != nil {
		t.Errorf("expected zero values, got %+v", acct)
	}
}

func newRevokeClient(t *testing.T, creds *mobiscroll.TokenResponse) (*mobiscroll.Client, *testsupport.MockServer) {
	t.Helper()
	srv := testsupport.NewMockServer(t)
	c := mobiscroll.NewClient("id", "secret", "https://app/cb",
		mobiscroll.WithBaseURL(srv.URL+"/api"),
	)
	if creds != nil {
		c.SetCredentials(creds)
	}
	return c, srv
}

func assertRevokeRequest(t *testing.T, srv *testsupport.MockServer, wantToken string) {
	t.Helper()
	reqs := srv.Requests()
	if len(reqs) != 1 {
		t.Fatalf("expected 1 request, got %d", len(reqs))
	}
	if reqs[0].Method != "POST" || reqs[0].Path != "/api/oauth/revoke" {
		t.Fatalf("unexpected request: %s %s", reqs[0].Method, reqs[0].Path)
	}
	var body map[string]any
	if err := json.Unmarshal(reqs[0].Body, &body); err != nil {
		t.Fatalf("decode body: %v", err)
	}
	if len(body) != 1 || body["token"] != wantToken {
		t.Fatalf("unexpected body: %s", reqs[0].Body)
	}
}

func TestRevoke_ExplicitTokenKeepsCredentials(t *testing.T) {
	stored := &mobiscroll.TokenResponse{AccessToken: "at", RefreshToken: "rt"}
	c, srv := newRevokeClient(t, stored)
	srv.EnqueueJSON(`{"success":true}`)

	resp, err := c.Auth().Revoke(context.Background(), "explicit")
	if err != nil {
		t.Fatalf("Revoke: %v", err)
	}
	if !resp.Success {
		t.Fatalf("expected success=true")
	}
	assertRevokeRequest(t, srv, "explicit")
	if got := c.Credentials(); got != stored {
		t.Fatalf("credentials changed: %+v", got)
	}
}

func TestRevoke_DefaultsToStoredRefreshTokenAndClearsCredentials(t *testing.T) {
	c, srv := newRevokeClient(t, &mobiscroll.TokenResponse{AccessToken: "at", RefreshToken: "rt"})
	srv.EnqueueJSON(`{"success":true}`)

	if _, err := c.Auth().Revoke(context.Background(), ""); err != nil {
		t.Fatalf("Revoke: %v", err)
	}
	assertRevokeRequest(t, srv, "rt")
	if got := c.Credentials(); got != nil {
		t.Fatalf("expected credentials cleared, got %+v", got)
	}
}

func TestRevoke_FallsBackToStoredAccessToken(t *testing.T) {
	c, srv := newRevokeClient(t, &mobiscroll.TokenResponse{AccessToken: "at"})
	srv.EnqueueJSON(`{"success":true}`)

	if _, err := c.Auth().Revoke(context.Background(), ""); err != nil {
		t.Fatalf("Revoke: %v", err)
	}
	assertRevokeRequest(t, srv, "at")
	if got := c.Credentials(); got != nil {
		t.Fatalf("expected credentials cleared, got %+v", got)
	}
}

func TestRevoke_NoTokenReturnsValidationErrorWithoutRequest(t *testing.T) {
	c, srv := newRevokeClient(t, nil)

	_, err := c.Auth().Revoke(context.Background(), "")
	var ve *mobiscroll.ValidationError
	if !errors.As(err, &ve) {
		t.Fatalf("expected *ValidationError, got %T: %v", err, err)
	}
	if n := srv.RequestCount(); n != 0 {
		t.Fatalf("expected no requests, got %d", n)
	}
}

func TestRevoke_FailureKeepsCredentials(t *testing.T) {
	stored := &mobiscroll.TokenResponse{AccessToken: "at", RefreshToken: "rt"}
	c, srv := newRevokeClient(t, stored)
	srv.Enqueue(testsupport.MockResponse{Status: 400,
		Body:    `{"error":"invalid_request","error_description":"token is required"}`,
		Headers: map[string]string{"Content-Type": "application/json"}})

	_, err := c.Auth().Revoke(context.Background(), "")
	var ve *mobiscroll.ValidationError
	if !errors.As(err, &ve) {
		t.Fatalf("expected *ValidationError, got %T: %v", err, err)
	}
	if got := c.Credentials(); got != stored {
		t.Fatalf("credentials changed: %+v", got)
	}
}

func TestRevoke_DoesNotRefreshOn401(t *testing.T) {
	stored := &mobiscroll.TokenResponse{AccessToken: "at", RefreshToken: "rt"}
	c, srv := newRevokeClient(t, stored)
	srv.EnqueueStatus(401)
	srv.EnqueueJSON(`{"access_token":"new","refresh_token":"new-rt"}`)

	_, err := c.Auth().Revoke(context.Background(), "")
	var ae *mobiscroll.AuthenticationError
	if !errors.As(err, &ae) {
		t.Fatalf("expected *AuthenticationError, got %T: %v", err, err)
	}
	assertRevokeRequest(t, srv, "rt")
	if got := c.Credentials(); got != stored {
		t.Fatalf("credentials changed: %+v", got)
	}
}
