package mobiscroll

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"

	"github.com/acidb/mobiscroll-connect-sdks/sdks/go/internal/querybuilder"
)

// eventsService groups the events endpoints.
type eventsService struct{ api *apiClient }

// List returns a paginated slice of events across the calendars selected by
// params. Pass nil for default behavior (events for all connected calendars,
// server-default page size). The CalendarIDs map is serialized as a single
// JSON-encoded query parameter (e.g. calendarIds={"google":["primary"]}),
// matching the wire format of the other SDKs.
func (s *eventsService) List(ctx context.Context, params *EventListParams) (*EventsListResponse, error) {
	qs := querybuilder.New()
	if params != nil {
		if len(params.CalendarIDs) > 0 {
			wire := make(map[string][]string, len(params.CalendarIDs))
			for k, v := range params.CalendarIDs {
				wire[string(k)] = v
			}
			encoded, err := json.Marshal(wire)
			if err != nil {
				return nil, fmt.Errorf("encode calendarIds: %w", err)
			}
			qs.Add("calendarIds", string(encoded))
		}
		qs.Add("start", params.Start).
			Add("end", params.End).
			Add("pageSize", params.PageSize).
			Add("nextPageToken", params.NextPageToken).
			Add("singleEvents", params.SingleEvents)
	}
	out := &EventsListResponse{}
	if err := s.api.do(ctx, http.MethodGet, "/events", qs.Encode(), nil, out); err != nil {
		return nil, err
	}
	return out, nil
}

// Create creates a new event and returns the server-assigned representation.
func (s *eventsService) Create(ctx context.Context, data *EventCreateData) (*CalendarEvent, error) {
	if data == nil {
		return nil, errors.New("mobiscroll: EventCreateData must not be nil")
	}
	out := &CalendarEvent{}
	if err := s.api.do(ctx, http.MethodPost, "/event", "", data, out); err != nil {
		return nil, err
	}
	return out, nil
}

// Update updates an existing event. EventID is required; non-zero fields are
// applied as patches.
func (s *eventsService) Update(ctx context.Context, data *EventUpdateData) (*CalendarEvent, error) {
	if data == nil {
		return nil, errors.New("mobiscroll: EventUpdateData must not be nil")
	}
	out := &CalendarEvent{}
	if err := s.api.do(ctx, http.MethodPut, "/event", "", data, out); err != nil {
		return nil, err
	}
	return out, nil
}

// Delete deletes an event (or a slice of a recurring series, when
// RecurringEventID + DeleteMode are set).
func (s *eventsService) Delete(ctx context.Context, params *EventDeleteParams) error {
	if params == nil {
		return errors.New("mobiscroll: EventDeleteParams must not be nil")
	}
	qs := querybuilder.New().
		Add("provider", string(params.Provider)).
		Add("calendarId", params.CalendarID).
		Add("eventId", params.EventID).
		Add("recurringEventId", params.RecurringEventID).
		Add("deleteMode", params.DeleteMode).
		Encode()
	return s.api.do(ctx, http.MethodDelete, "/event", qs, nil, nil)
}
