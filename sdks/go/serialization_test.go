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
