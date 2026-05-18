package mobiscroll_test

import (
	"context"
	"strings"
	"sync"
	"sync/atomic"
	"testing"

	mobiscroll "github.com/acidb/mobiscroll-connect-sdks/sdks/go"
	"github.com/acidb/mobiscroll-connect-sdks/sdks/go/testsupport"
)

// TestRefreshOn401AndRetry covers the happy path: a 401 triggers a refresh
// and the original request is replayed once with the new bearer.
func TestRefreshOn401AndRetry(t *testing.T) {
	srv := testsupport.NewMockServer(t)
	srv.Enqueue(testsupport.MockResponse{Status: 401, Body: `{"message":"expired"}`,
		Headers: map[string]string{"Content-Type": "application/json"}})
	srv.EnqueueJSON(`{"access_token":"new-at","token_type":"Bearer","expires_in":3600,"refresh_token":"new-rt"}`)
	srv.EnqueueJSON(`[]`)

	var refreshed *mobiscroll.TokenResponse
	c := mobiscroll.NewClient("id", "secret", "https://app/cb",
		mobiscroll.WithBaseURL(srv.URL+"/api"),
		mobiscroll.WithTokensRefreshedCallback(func(t *mobiscroll.TokenResponse) { refreshed = t }),
	)
	c.SetCredentials(&mobiscroll.TokenResponse{AccessToken: "old-at", RefreshToken: "old-rt"})

	if _, err := c.Calendars().List(context.Background()); err != nil {
		t.Fatalf("List: %v", err)
	}

	reqs := srv.Requests()
	if len(reqs) != 3 {
		t.Fatalf("expected 3 requests (init, refresh, retry), got %d", len(reqs))
	}
	if reqs[0].Path != "/api/calendars" || reqs[0].Header.Get("Authorization") != "Bearer old-at" {
		t.Errorf("first request: %s %q", reqs[0].Path, reqs[0].Header.Get("Authorization"))
	}
	if reqs[1].Path != "/api/oauth/token" || !strings.HasPrefix(reqs[1].Header.Get("Authorization"), "Basic ") {
		t.Errorf("refresh request: %s %q", reqs[1].Path, reqs[1].Header.Get("Authorization"))
	}
	if !strings.Contains(string(reqs[1].Body), "grant_type=refresh_token") {
		t.Errorf("refresh body: %s", reqs[1].Body)
	}
	if reqs[2].Path != "/api/calendars" || reqs[2].Header.Get("Authorization") != "Bearer new-at" {
		t.Errorf("retry: %s %q", reqs[2].Path, reqs[2].Header.Get("Authorization"))
	}
	if refreshed == nil || refreshed.AccessToken != "new-at" {
		t.Errorf("callback not invoked correctly: %+v", refreshed)
	}
}

// TestRefreshPreservesOldRefreshToken: when the server omits refresh_token on
// the refresh response, the SDK keeps the existing one.
func TestRefreshPreservesOldRefreshToken(t *testing.T) {
	srv := testsupport.NewMockServer(t)
	srv.Enqueue(testsupport.MockResponse{Status: 401})
	srv.EnqueueJSON(`{"access_token":"new-at","token_type":"Bearer","expires_in":3600}`)
	srv.EnqueueJSON(`[]`)

	c := mobiscroll.NewClient("id", "secret", "https://app/cb",
		mobiscroll.WithBaseURL(srv.URL+"/api"),
	)
	c.SetCredentials(&mobiscroll.TokenResponse{AccessToken: "old-at", RefreshToken: "old-rt"})

	if _, err := c.Calendars().List(context.Background()); err != nil {
		t.Fatalf("List: %v", err)
	}
	got := c.Credentials()
	if got == nil || got.RefreshToken != "old-rt" || got.AccessToken != "new-at" {
		t.Fatalf("expected refresh token preserved; got %+v", got)
	}
}

// TestRefreshDedup is the critical concurrency test: N parallel 401-returning
// requests must trigger exactly ONE refresh.
func TestRefreshDedup(t *testing.T) {
	const N = 10
	srv := testsupport.NewMockServer(t)
	// Queue: N initial 401s, then ONE token refresh, then N successful retries.
	for i := 0; i < N; i++ {
		srv.Enqueue(testsupport.MockResponse{Status: 401})
	}
	srv.EnqueueJSON(`{"access_token":"new-at","token_type":"Bearer","expires_in":3600,"refresh_token":"new-rt"}`)
	for i := 0; i < N; i++ {
		srv.EnqueueJSON(`[]`)
	}

	c := mobiscroll.NewClient("id", "secret", "https://app/cb",
		mobiscroll.WithBaseURL(srv.URL+"/api"),
	)
	c.SetCredentials(&mobiscroll.TokenResponse{AccessToken: "old-at", RefreshToken: "old-rt"})

	var wg sync.WaitGroup
	var failures atomic.Int32
	for i := 0; i < N; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			if _, err := c.Calendars().List(context.Background()); err != nil {
				failures.Add(1)
			}
		}()
	}
	wg.Wait()
	if failures.Load() != 0 {
		t.Fatalf("expected zero failures, got %d", failures.Load())
	}

	// Count refresh requests across the recorded set.
	refreshes := 0
	for _, r := range srv.Requests() {
		if r.Path == "/api/oauth/token" {
			refreshes++
		}
	}
	if refreshes != 1 {
		t.Fatalf("expected exactly 1 refresh (singleflight dedup), got %d", refreshes)
	}
}
