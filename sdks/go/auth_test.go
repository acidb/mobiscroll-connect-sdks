package mobiscroll_test

import (
	"context"
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
