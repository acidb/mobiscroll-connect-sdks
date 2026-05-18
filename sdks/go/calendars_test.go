package mobiscroll_test

import (
	"context"
	"testing"

	mobiscroll "github.com/acidb/mobiscroll-connect-sdks/sdks/go"
	"github.com/acidb/mobiscroll-connect-sdks/sdks/go/testsupport"
)

func TestCalendars_ListParses(t *testing.T) {
	srv := testsupport.NewMockServer(t)
	srv.EnqueueJSON(`[{"id":"cal1","provider":"google","title":"Work","timeZone":"UTC","color":"#4285F4","description":""}]`)

	c := mobiscroll.NewClient("id", "secret", "https://app/cb",
		mobiscroll.WithBaseURL(srv.URL+"/api"),
	)
	c.SetCredentials(&mobiscroll.TokenResponse{AccessToken: "at"})

	cals, err := c.Calendars().List(context.Background())
	if err != nil {
		t.Fatalf("List: %v", err)
	}
	if len(cals) != 1 {
		t.Fatalf("expected 1 calendar, got %d", len(cals))
	}
	got := cals[0]
	if got.ID != "cal1" || got.Provider != mobiscroll.ProviderGoogle || got.Title != "Work" || got.Color != "#4285F4" {
		t.Fatalf("unexpected calendar: %+v", got)
	}

	reqs := srv.Requests()
	if reqs[0].Method != "GET" || reqs[0].Path != "/api/calendars" {
		t.Fatalf("unexpected request: %s %s", reqs[0].Method, reqs[0].Path)
	}
	if got := reqs[0].Header.Get("Authorization"); got != "Bearer at" {
		t.Errorf("expected Bearer header, got %q", got)
	}
}
