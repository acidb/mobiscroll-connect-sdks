package mobiscroll_test

import (
	"context"
	"errors"
	"strings"
	"testing"

	mobiscroll "github.com/acidb/mobiscroll-connect-sdks/sdks/go"
	"github.com/acidb/mobiscroll-connect-sdks/sdks/go/testsupport"
)

func TestWebhooks_SubscribeWebhookSendsJSONBodyAndParsesResponse(t *testing.T) {
	srv := testsupport.NewMockServer(t)
	srv.EnqueueJSON(`{
		"success": true,
		"provider": "google",
		"subscription": {"channelId": "chan1", "resourceId": "res1", "expiration": "2026-06-01T00:00:00.000Z"},
		"serverWebhookUrl": "https://connect.mobiscroll.com/api/webhook-callback",
		"channelId": "chan1"
	}`)

	c := mobiscroll.NewClient("id", "secret", "https://app/cb",
		mobiscroll.WithBaseURL(srv.URL+"/api"),
	)
	c.SetCredentials(&mobiscroll.TokenResponse{AccessToken: "at"})

	resp, err := c.Webhooks().SubscribeWebhook(context.Background(), &mobiscroll.SubscribeWebhookParams{
		Provider:   mobiscroll.ProviderGoogle,
		CalendarID: "primary",
		Expiration: mobiscroll.Ptr(int64(1780000000000)),
	})
	if err != nil {
		t.Fatalf("SubscribeWebhook: %v", err)
	}
	if !resp.Success || resp.Provider != "google" || resp.ChannelID != "chan1" {
		t.Fatalf("unexpected response: %+v", resp)
	}
	if resp.Subscription.ResourceID != "res1" || resp.Subscription.Expiration != "2026-06-01T00:00:00.000Z" {
		t.Fatalf("unexpected subscription: %+v", resp.Subscription)
	}
	if resp.ServerWebhookURL == "" {
		t.Fatalf("expected ServerWebhookURL to be set, got %+v", resp)
	}

	req := srv.Requests()[0]
	if req.Method != "POST" || req.Path != "/api/subscribe-webhook" {
		t.Fatalf("unexpected request: %s %s", req.Method, req.Path)
	}
	if !strings.HasPrefix(req.Header.Get("Content-Type"), "application/json") {
		t.Errorf("expected JSON content type, got %q", req.Header.Get("Content-Type"))
	}
	if got := req.Header.Get("Authorization"); got != "Bearer at" {
		t.Errorf("expected Bearer header, got %q", got)
	}
	body := string(req.Body)
	for _, want := range []string{`"provider":"google"`, `"calendarId":"primary"`, `"expiration":1780000000000`} {
		if !strings.Contains(body, want) {
			t.Errorf("body missing %q: %s", want, body)
		}
	}
	if strings.Contains(body, "channelId") {
		t.Errorf("expected channelId to be omitted when unset, got body: %s", body)
	}
}

func TestWebhooks_SubscribeWebhookNilParams(t *testing.T) {
	c := mobiscroll.NewClient("id", "secret", "https://app/cb")
	if _, err := c.Webhooks().SubscribeWebhook(context.Background(), nil); err == nil {
		t.Fatal("expected error for nil params")
	}
}

func TestWebhooks_SubscribeWebhookValidationError(t *testing.T) {
	srv := testsupport.NewMockServer(t)
	srv.Enqueue(testsupport.MockResponse{
		Status:  400,
		Body:    `{"message":"calendarId is required"}`,
		Headers: map[string]string{"Content-Type": "application/json"},
	})

	c := mobiscroll.NewClient("id", "secret", "https://app/cb",
		mobiscroll.WithBaseURL(srv.URL+"/api"),
	)
	c.SetCredentials(&mobiscroll.TokenResponse{AccessToken: "at"})

	_, err := c.Webhooks().SubscribeWebhook(context.Background(), &mobiscroll.SubscribeWebhookParams{
		Provider: mobiscroll.ProviderGoogle,
	})
	var ve *mobiscroll.ValidationError
	if !errors.As(err, &ve) {
		t.Fatalf("expected *ValidationError, got %T: %v", err, err)
	}
}

func TestWebhooks_SubscribeWebhookAuthError(t *testing.T) {
	srv := testsupport.NewMockServer(t)
	// SDK retries once on 401 after a refresh attempt; queue a 401 for both.
	srv.Enqueue(testsupport.MockResponse{Status: 401, Body: `{"message":"bad token"}`,
		Headers: map[string]string{"Content-Type": "application/json"}})
	srv.Enqueue(testsupport.MockResponse{Status: 401, Body: `{"message":"refresh failed"}`,
		Headers: map[string]string{"Content-Type": "application/json"}})

	c := mobiscroll.NewClient("id", "secret", "https://app/cb",
		mobiscroll.WithBaseURL(srv.URL+"/api"),
	)
	c.SetCredentials(&mobiscroll.TokenResponse{AccessToken: "at", RefreshToken: "rt"})

	_, err := c.Webhooks().SubscribeWebhook(context.Background(), &mobiscroll.SubscribeWebhookParams{
		Provider:   mobiscroll.ProviderGoogle,
		CalendarID: "primary",
	})
	var ae *mobiscroll.AuthenticationError
	if !errors.As(err, &ae) {
		t.Fatalf("expected *AuthenticationError, got %T: %v", err, err)
	}
}

func TestWebhooks_UnsubscribeWebhookSendsJSONBodyAndParsesResponse(t *testing.T) {
	srv := testsupport.NewMockServer(t)
	srv.EnqueueJSON(`{"success": true}`)

	c := mobiscroll.NewClient("id", "secret", "https://app/cb",
		mobiscroll.WithBaseURL(srv.URL+"/api"),
	)
	c.SetCredentials(&mobiscroll.TokenResponse{AccessToken: "at"})

	resp, err := c.Webhooks().UnsubscribeWebhook(context.Background(), &mobiscroll.UnsubscribeWebhookParams{
		Provider:   mobiscroll.ProviderGoogle,
		ChannelID:  "chan1",
		ResourceID: "res1",
	})
	if err != nil {
		t.Fatalf("UnsubscribeWebhook: %v", err)
	}
	if !resp.Success {
		t.Fatalf("unexpected response: %+v", resp)
	}

	req := srv.Requests()[0]
	if req.Method != "POST" || req.Path != "/api/unsubscribe-webhook" {
		t.Fatalf("unexpected request: %s %s", req.Method, req.Path)
	}
	body := string(req.Body)
	for _, want := range []string{`"provider":"google"`, `"channelId":"chan1"`, `"resourceId":"res1"`} {
		if !strings.Contains(body, want) {
			t.Errorf("body missing %q: %s", want, body)
		}
	}
}

// TestWebhooks_UnsubscribeWebhookSuccessDespiteMessage covers the documented
// case where the backend already removed the local mapping and reports
// success even though the provider-side unsubscribe itself failed (e.g. an
// already-expired subscription). Callers should treat 200 as final.
func TestWebhooks_UnsubscribeWebhookSuccessDespiteMessage(t *testing.T) {
	srv := testsupport.NewMockServer(t)
	srv.EnqueueJSON(`{"success": true, "message": "provider subscription already expired"}`)

	c := mobiscroll.NewClient("id", "secret", "https://app/cb",
		mobiscroll.WithBaseURL(srv.URL+"/api"),
	)
	c.SetCredentials(&mobiscroll.TokenResponse{AccessToken: "at"})

	resp, err := c.Webhooks().UnsubscribeWebhook(context.Background(), &mobiscroll.UnsubscribeWebhookParams{
		Provider:  mobiscroll.ProviderGoogle,
		ChannelID: "chan1",
	})
	if err != nil {
		t.Fatalf("UnsubscribeWebhook: %v", err)
	}
	if !resp.Success || resp.Message == "" {
		t.Fatalf("expected success=true with an explanatory message, got %+v", resp)
	}
}

func TestWebhooks_UnsubscribeWebhookNilParams(t *testing.T) {
	c := mobiscroll.NewClient("id", "secret", "https://app/cb")
	if _, err := c.Webhooks().UnsubscribeWebhook(context.Background(), nil); err == nil {
		t.Fatal("expected error for nil params")
	}
}

func TestWebhooks_UnsubscribeWebhookValidationError(t *testing.T) {
	srv := testsupport.NewMockServer(t)
	srv.Enqueue(testsupport.MockResponse{
		Status:  400,
		Body:    `{"message":"channelId is required"}`,
		Headers: map[string]string{"Content-Type": "application/json"},
	})

	c := mobiscroll.NewClient("id", "secret", "https://app/cb",
		mobiscroll.WithBaseURL(srv.URL+"/api"),
	)
	c.SetCredentials(&mobiscroll.TokenResponse{AccessToken: "at"})

	_, err := c.Webhooks().UnsubscribeWebhook(context.Background(), &mobiscroll.UnsubscribeWebhookParams{
		Provider: mobiscroll.ProviderGoogle,
	})
	var ve *mobiscroll.ValidationError
	if !errors.As(err, &ve) {
		t.Fatalf("expected *ValidationError, got %T: %v", err, err)
	}
}

func TestWebhooks_UnsubscribeWebhookAuthError(t *testing.T) {
	srv := testsupport.NewMockServer(t)
	srv.Enqueue(testsupport.MockResponse{Status: 401, Body: `{"message":"bad token"}`,
		Headers: map[string]string{"Content-Type": "application/json"}})
	srv.Enqueue(testsupport.MockResponse{Status: 401, Body: `{"message":"refresh failed"}`,
		Headers: map[string]string{"Content-Type": "application/json"}})

	c := mobiscroll.NewClient("id", "secret", "https://app/cb",
		mobiscroll.WithBaseURL(srv.URL+"/api"),
	)
	c.SetCredentials(&mobiscroll.TokenResponse{AccessToken: "at", RefreshToken: "rt"})

	_, err := c.Webhooks().UnsubscribeWebhook(context.Background(), &mobiscroll.UnsubscribeWebhookParams{
		Provider:  mobiscroll.ProviderGoogle,
		ChannelID: "chan1",
	})
	var ae *mobiscroll.AuthenticationError
	if !errors.As(err, &ae) {
		t.Fatalf("expected *AuthenticationError, got %T: %v", err, err)
	}
}
