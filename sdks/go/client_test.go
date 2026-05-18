package mobiscroll_test

import (
	"net/http"
	"testing"
	"time"

	mobiscroll "github.com/acidb/mobiscroll-connect-sdks/sdks/go"
)

func TestNewClient_AppliesOptions(t *testing.T) {
	custom := &http.Client{Timeout: 5 * time.Second}
	c := mobiscroll.NewClient("id", "secret", "https://app/cb",
		mobiscroll.WithBaseURL("https://example.test/api"),
		mobiscroll.WithTimeout(7*time.Second),
		mobiscroll.WithHTTPClient(custom),
	)
	if c.Auth() == nil || c.Calendars() == nil || c.Events() == nil {
		t.Fatalf("expected all resources wired; got nil")
	}
	if c.Credentials() != nil {
		t.Fatalf("expected no credentials before SetCredentials")
	}
}

func TestSetCredentials_RoundTrip(t *testing.T) {
	c := mobiscroll.NewClient("id", "secret", "https://app/cb")
	tok := &mobiscroll.TokenResponse{AccessToken: "abc", RefreshToken: "rfr"}
	c.SetCredentials(tok)
	got := c.Credentials()
	if got == nil || got.AccessToken != "abc" || got.RefreshToken != "rfr" {
		t.Fatalf("credentials round-trip failed: got %+v", got)
	}
}
