package mobiscroll_test

import (
	"encoding/json"
	"strings"
	"testing"

	mobiscroll "github.com/acidb/mobiscroll-connect-sdks/sdks/go"
)

func TestProvider_JSONRoundTrip(t *testing.T) {
	b, err := json.Marshal(mobiscroll.ProviderGoogle)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}
	if string(b) != `"google"` {
		t.Fatalf("expected lowercase wire form, got %s", b)
	}
	var p mobiscroll.Provider
	if err := json.Unmarshal([]byte(`"microsoft"`), &p); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	if p != mobiscroll.ProviderMicrosoft {
		t.Fatalf("expected microsoft, got %q", p)
	}
}

func TestEventCreateData_OmitsOptionalFields(t *testing.T) {
	data := &mobiscroll.EventCreateData{
		Provider:   mobiscroll.ProviderGoogle,
		CalendarID: "c1",
		Title:      "T",
	}
	b, err := json.Marshal(data)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}
	s := string(b)
	for _, banned := range []string{"description", "location", "allDay", "attendees", "recurrence", "custom", "availability", "privacy", "status"} {
		if strings.Contains(s, `"`+banned+`"`) {
			t.Errorf("expected %q to be omitted, got %s", banned, s)
		}
	}
}

func TestCalendarEvent_UnmarshalsDocumentedOptionalFields(t *testing.T) {
	const payload = `{
		"provider": "google",
		"id": "evt-1",
		"calendarId": "primary",
		"title": "Standup",
		"description": "Weekly team sync",
		"conference": "https://meet.google.com/abc-defg-hij",
		"conferenceData": {"provider": "google-meet", "conferenceId": "abc-defg-hij"},
		"lastModified": "2026-03-10T13:36:08.000Z"
	}`

	var e mobiscroll.CalendarEvent
	if err := json.Unmarshal([]byte(payload), &e); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	if e.Description != "Weekly team sync" {
		t.Errorf("description: got %q", e.Description)
	}
	if got := e.ConferenceData["provider"]; got != "google-meet" {
		t.Errorf("conferenceData.provider: got %v", got)
	}
	if e.LastModified != "2026-03-10T13:36:08.000Z" {
		t.Errorf("lastModified: got %q", e.LastModified)
	}
}
