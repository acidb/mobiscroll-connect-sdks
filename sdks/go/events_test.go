package mobiscroll_test

import (
	"context"
	"net/url"
	"strings"
	"testing"
	"time"

	mobiscroll "github.com/acidb/mobiscroll-connect-sdks/sdks/go"
	"github.com/acidb/mobiscroll-connect-sdks/sdks/go/testsupport"
)

func TestEvents_ListEncodesCalendarIDsAndQuery(t *testing.T) {
	srv := testsupport.NewMockServer(t)
	srv.EnqueueJSON(`{"events":[{"id":"e1","provider":"google","calendarId":"c1","title":"Meet"}],"pageSize":50,"nextPageToken":"tok"}`)

	c := mobiscroll.NewClient("id", "secret", "https://app/cb",
		mobiscroll.WithBaseURL(srv.URL+"/api"),
	)
	c.SetCredentials(&mobiscroll.TokenResponse{AccessToken: "at"})

	start := time.Date(2026, 5, 1, 0, 0, 0, 0, time.UTC)
	end := time.Date(2026, 5, 31, 23, 59, 59, 0, time.UTC)
	resp, err := c.Events().List(context.Background(), &mobiscroll.EventListParams{
		CalendarIDs:  map[mobiscroll.Provider][]string{mobiscroll.ProviderGoogle: {"c1", "c2"}},
		Start:        &start,
		End:          &end,
		PageSize:     mobiscroll.Ptr(50),
		SingleEvents: mobiscroll.Ptr(true),
	})
	if err != nil {
		t.Fatalf("List: %v", err)
	}
	if len(resp.Events) != 1 || resp.NextPageToken != "tok" || resp.PageSize == nil || *resp.PageSize != 50 {
		t.Fatalf("unexpected response: %+v", resp)
	}

	req := srv.Requests()[0]
	if !strings.HasPrefix(req.Path, "/api/events?") {
		t.Fatalf("unexpected path: %s", req.Path)
	}
	u, _ := url.Parse(req.Path)
	if got := u.Query().Get("calendarIds"); got != `{"google":["c1","c2"]}` {
		t.Errorf("calendarIds wire format wrong: %s", got)
	}
	for _, want := range []string{"singleEvents=true", "pageSize=50"} {
		if !strings.Contains(req.Path, want) {
			t.Errorf("path missing %q: %s", want, req.Path)
		}
	}
}

func TestEvents_CreateSendsJSONBodyToSingularPath(t *testing.T) {
	srv := testsupport.NewMockServer(t)
	srv.EnqueueJSON(`{"id":"e1","provider":"google","calendarId":"c1","title":"Meet","start":"2026-05-01T09:00:00Z","end":"2026-05-01T10:00:00Z"}`)

	c := mobiscroll.NewClient("id", "secret", "https://app/cb",
		mobiscroll.WithBaseURL(srv.URL+"/api"),
	)
	c.SetCredentials(&mobiscroll.TokenResponse{AccessToken: "at"})

	ev, err := c.Events().Create(context.Background(), &mobiscroll.EventCreateData{
		Provider:   mobiscroll.ProviderGoogle,
		CalendarID: "c1",
		Title:      "Meet",
		Start:      time.Date(2026, 5, 1, 9, 0, 0, 0, time.UTC),
		End:        time.Date(2026, 5, 1, 10, 0, 0, 0, time.UTC),
	})
	if err != nil {
		t.Fatalf("Create: %v", err)
	}
	if ev.ID != "e1" {
		t.Fatalf("unexpected event id: %s", ev.ID)
	}

	req := srv.Requests()[0]
	if req.Method != "POST" || req.Path != "/api/event" {
		t.Fatalf("unexpected request: %s %s", req.Method, req.Path)
	}
	if !strings.HasPrefix(req.Header.Get("Content-Type"), "application/json") {
		t.Errorf("expected JSON content type, got %q", req.Header.Get("Content-Type"))
	}
	body := string(req.Body)
	for _, want := range []string{`"provider":"google"`, `"title":"Meet"`, `"start":"2026-05-01T09:00:00Z"`} {
		if !strings.Contains(body, want) {
			t.Errorf("body missing %q: %s", want, body)
		}
	}
}

func TestEvents_UpdatePutsToSingularPath(t *testing.T) {
	srv := testsupport.NewMockServer(t)
	srv.EnqueueJSON(`{"id":"e1","provider":"google","calendarId":"c1","title":"Renamed"}`)

	c := mobiscroll.NewClient("id", "secret", "https://app/cb",
		mobiscroll.WithBaseURL(srv.URL+"/api"),
	)
	c.SetCredentials(&mobiscroll.TokenResponse{AccessToken: "at"})

	ev, err := c.Events().Update(context.Background(), &mobiscroll.EventUpdateData{
		Provider: mobiscroll.ProviderGoogle, CalendarID: "c1", EventID: "e1", Title: "Renamed",
	})
	if err != nil {
		t.Fatalf("Update: %v", err)
	}
	if ev.Title != "Renamed" {
		t.Fatalf("expected renamed event, got %+v", ev)
	}
	req := srv.Requests()[0]
	if req.Method != "PUT" || req.Path != "/api/event" {
		t.Fatalf("unexpected request: %s %s", req.Method, req.Path)
	}
}

func TestEvents_DeleteSendsQueryParams(t *testing.T) {
	srv := testsupport.NewMockServer(t)
	srv.EnqueueStatus(204)

	c := mobiscroll.NewClient("id", "secret", "https://app/cb",
		mobiscroll.WithBaseURL(srv.URL+"/api"),
	)
	c.SetCredentials(&mobiscroll.TokenResponse{AccessToken: "at"})

	err := c.Events().Delete(context.Background(), &mobiscroll.EventDeleteParams{
		Provider: mobiscroll.ProviderGoogle, CalendarID: "c1", EventID: "e1", DeleteMode: "all",
	})
	if err != nil {
		t.Fatalf("Delete: %v", err)
	}
	req := srv.Requests()[0]
	if req.Method != "DELETE" || !strings.HasPrefix(req.Path, "/api/event?") {
		t.Fatalf("unexpected request: %s %s", req.Method, req.Path)
	}
	for _, want := range []string{"provider=google", "calendarId=c1", "eventId=e1", "deleteMode=all"} {
		if !strings.Contains(req.Path, want) {
			t.Errorf("path missing %q: %s", want, req.Path)
		}
	}
}
