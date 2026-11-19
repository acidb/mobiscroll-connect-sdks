package mobiscroll_test

import (
	"context"
	"encoding/json"
	"net/url"
	"reflect"
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

func newListTestClient(t *testing.T, body string) (*mobiscroll.Client, *testsupport.MockServer) {
	t.Helper()
	srv := testsupport.NewMockServer(t)
	srv.EnqueueJSON(body)
	c := mobiscroll.NewClient("id", "secret", "https://app/cb",
		mobiscroll.WithBaseURL(srv.URL+"/api"),
	)
	c.SetCredentials(&mobiscroll.TokenResponse{AccessToken: "at"})
	return c, srv
}

func TestEvents_ListEncodesEventIDsAsJSON(t *testing.T) {
	c, srv := newListTestClient(t, `{"events":[]}`)

	eventIDs := map[mobiscroll.Provider]map[string][]string{
		mobiscroll.ProviderGoogle:    {"ana@example.com": {"abc123", "def456"}},
		mobiscroll.ProviderMicrosoft: {"AAMk": {"x1"}},
	}
	if _, err := c.Events().List(context.Background(), &mobiscroll.EventListParams{EventIDs: eventIDs}); err != nil {
		t.Fatalf("List: %v", err)
	}

	u, _ := url.Parse(srv.Requests()[0].Path)
	var got map[string]map[string][]string
	if err := json.Unmarshal([]byte(u.Query().Get("eventIds")), &got); err != nil {
		t.Fatalf("eventIds is not JSON: %v", err)
	}
	want := map[string]map[string][]string{
		"google":    {"ana@example.com": {"abc123", "def456"}},
		"microsoft": {"AAMk": {"x1"}},
	}
	if !reflect.DeepEqual(got, want) {
		t.Errorf("eventIds = %v, want %v", got, want)
	}
}

func TestEvents_ListEventIDsWithSingleEventsFalse(t *testing.T) {
	c, srv := newListTestClient(t, `{"events":[]}`)

	_, err := c.Events().List(context.Background(), &mobiscroll.EventListParams{
		EventIDs:     map[mobiscroll.Provider]map[string][]string{mobiscroll.ProviderGoogle: {"ana@example.com": {"abc123"}}},
		SingleEvents: mobiscroll.Ptr(false),
	})
	if err != nil {
		t.Fatalf("List: %v", err)
	}

	u, _ := url.Parse(srv.Requests()[0].Path)
	q := u.Query()
	if q.Get("singleEvents") != "false" {
		t.Errorf("singleEvents = %q, want false", q.Get("singleEvents"))
	}
	if got := q.Get("eventIds"); got != `{"google":{"ana@example.com":["abc123"]}}` {
		t.Errorf("eventIds = %s", got)
	}
	if q.Has("calendarIds") {
		t.Errorf("calendarIds must not be sent: %s", u.RawQuery)
	}
}

func TestEvents_ListSendsEmptyEventIDsAsEmptyObject(t *testing.T) {
	c, srv := newListTestClient(t, `{"events":[]}`)

	_, err := c.Events().List(context.Background(), &mobiscroll.EventListParams{
		EventIDs: map[mobiscroll.Provider]map[string][]string{},
	})
	if err != nil {
		t.Fatalf("List: %v", err)
	}

	u, _ := url.Parse(srv.Requests()[0].Path)
	if !u.Query().Has("eventIds") || u.Query().Get("eventIds") != "{}" {
		t.Errorf("an empty eventIds must be sent as {}, got query %q", u.RawQuery)
	}
}

func TestEvents_ListSendsIncludeCancelledOnlyWhenSet(t *testing.T) {
	for _, tc := range []struct {
		name  string
		value *bool
		want  string // empty: not sent
	}{
		{"true", mobiscroll.Ptr(true), "true"},
		{"false", mobiscroll.Ptr(false), "false"},
		{"unset", nil, ""},
	} {
		t.Run(tc.name, func(t *testing.T) {
			c, srv := newListTestClient(t, `{"events":[]}`)
			_, err := c.Events().List(context.Background(), &mobiscroll.EventListParams{
				SingleEvents:     mobiscroll.Ptr(true),
				IncludeCancelled: tc.value,
				EventIDs:         nil,
			})
			if err != nil {
				t.Fatalf("List: %v", err)
			}
			path := srv.Requests()[0].Path
			u, _ := url.Parse(path)
			if tc.want == "" {
				if u.Query().Has("includeCancelled") || u.Query().Has("eventIds") {
					t.Errorf("includeCancelled/eventIds must not be sent: %s", path)
				}
				if path != "/api/events?singleEvents=true" {
					t.Errorf("unexpected path: %s", path)
				}
				return
			}
			if got := u.Query().Get("includeCancelled"); got != tc.want {
				t.Errorf("includeCancelled = %q, want %q", got, tc.want)
			}
		})
	}
}

func TestEvents_ListReadsInfo(t *testing.T) {
	c, _ := newListTestClient(t, `{"events":[],"info":{"google":"No connected account can read calendar ana@example.com"}}`)
	resp, err := c.Events().List(context.Background(), &mobiscroll.EventListParams{
		EventIDs: map[mobiscroll.Provider]map[string][]string{mobiscroll.ProviderGoogle: {"ana@example.com": {"abc123"}}},
	})
	if err != nil {
		t.Fatalf("List: %v", err)
	}
	want := map[mobiscroll.Provider]string{mobiscroll.ProviderGoogle: "No connected account can read calendar ana@example.com"}
	if !reflect.DeepEqual(resp.Info, want) {
		t.Errorf("Info = %v, want %v", resp.Info, want)
	}
}

func TestEvents_ListInfoIsNilWhenAbsent(t *testing.T) {
	c, _ := newListTestClient(t, `{"events":[]}`)
	resp, err := c.Events().List(context.Background(), nil)
	if err != nil {
		t.Fatalf("List: %v", err)
	}
	if resp.Info != nil {
		t.Errorf("Info = %v, want nil", resp.Info)
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
		Conference: "https://meet.example.com/x",
		ConferenceData: &mobiscroll.EventConferenceData{
			AutoGenerate: mobiscroll.Ptr(true),
		},
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
	for _, want := range []string{
		`"provider":"google"`, `"title":"Meet"`, `"start":"2026-05-01T09:00:00Z"`,
		`"conference":"https://meet.example.com/x"`, `"conferenceData":{"autoGenerate":true}`,
	} {
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
