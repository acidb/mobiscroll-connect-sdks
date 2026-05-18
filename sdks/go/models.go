package mobiscroll

import (
	"encoding/json"
	"time"
)

// Ptr returns a pointer to v. It is a convenience for filling out optional
// pointer fields on parameter structs inline:
//
//	client.Events().List(ctx, &mobiscroll.EventListParams{PageSize: mobiscroll.Ptr(50)})
func Ptr[T any](v T) *T { return &v }

// TokenResponse is the OAuth2 token payload returned by the API.
type TokenResponse struct {
	AccessToken  string `json:"access_token"`
	TokenType    string `json:"token_type,omitempty"`
	ExpiresIn    int    `json:"expires_in,omitempty"`
	RefreshToken string `json:"refresh_token,omitempty"`
}

// mergedWith returns a new TokenResponse that overlays incoming on top of t,
// preserving the existing refresh_token if incoming omits one.
func (t *TokenResponse) mergedWith(incoming *TokenResponse) *TokenResponse {
	out := *incoming
	if out.RefreshToken == "" && t != nil {
		out.RefreshToken = t.RefreshToken
	}
	return &out
}

// AuthURLParams is the input to Auth.GenerateAuthURL.
type AuthURLParams struct {
	UserID    string     // required
	Scope     string     // optional: "read-write" | "free-busy" | "read"
	State     string     // optional CSRF state
	Providers []Provider // optional: restrict to specific providers
}

// ConnectedAccount is one connected account under a provider.
type ConnectedAccount struct {
	ID      string `json:"id"`
	Display string `json:"display,omitempty"`
}

// ConnectionStatus is the result of Auth.GetConnectionStatus. Connections is
// keyed by lowercase provider name to match the API wire form.
type ConnectionStatus struct {
	Connections  map[Provider][]ConnectedAccount `json:"connections"`
	LimitReached bool                            `json:"limitReached"`
}

// DisconnectParams is the input to Auth.Disconnect.
type DisconnectParams struct {
	Provider Provider // required
	Account  string   // optional; disconnect this account only
}

// DisconnectResponse is the result of Auth.Disconnect.
type DisconnectResponse struct {
	Success bool   `json:"success"`
	Message string `json:"message,omitempty"`
}

// Calendar is a calendar exposed by one of the supported providers.
type Calendar struct {
	Provider    Provider        `json:"provider"`
	ID          string          `json:"id"`
	Title       string          `json:"title"`
	TimeZone    string          `json:"timeZone"`
	Color       string          `json:"color"`
	Description string          `json:"description"`
	Original    json.RawMessage `json:"original,omitempty"`
}

// Attendee is one attendee on an event.
type Attendee struct {
	Email     string `json:"email"`
	Status    string `json:"status,omitempty"`
	Organizer *bool  `json:"organizer,omitempty"`
}

// RecurrenceRule is an iCal-style recurrence rule. Frequency is one of
// "DAILY", "WEEKLY", "MONTHLY", "YEARLY".
type RecurrenceRule struct {
	Frequency  string   `json:"frequency"`
	Interval   *int     `json:"interval,omitempty"`
	Count      *int     `json:"count,omitempty"`
	Until      string   `json:"until,omitempty"`
	ByDay      []string `json:"byDay,omitempty"`
	ByMonthDay []int    `json:"byMonthDay,omitempty"`
	ByMonth    []int    `json:"byMonth,omitempty"`
}

// CalendarEvent is an event returned by the API. The field set mirrors the
// Node SDK's response shape exactly.
type CalendarEvent struct {
	Provider         Provider                   `json:"provider"`
	ID               string                     `json:"id"`
	CalendarID       string                     `json:"calendarId"`
	Title            string                     `json:"title"`
	Start            *time.Time                 `json:"start,omitempty"`
	End              *time.Time                 `json:"end,omitempty"`
	AllDay           bool                       `json:"allDay,omitempty"`
	RecurringEventID string                     `json:"recurringEventId,omitempty"`
	Color            string                     `json:"color,omitempty"`
	Location         string                     `json:"location,omitempty"`
	Attendees        []Attendee                 `json:"attendees,omitempty"`
	Custom           map[string]any             `json:"custom,omitempty"`
	Conference       string                     `json:"conference,omitempty"`
	Availability     string                     `json:"availability,omitempty"`
	Privacy          string                     `json:"privacy,omitempty"`
	Status           string                     `json:"status,omitempty"`
	Link             string                     `json:"link,omitempty"`
	Original         json.RawMessage            `json:"original,omitempty"`
	Additional       map[string]json.RawMessage `json:"-"`
}

// EventListParams is the input to Events.List. All fields are optional.
type EventListParams struct {
	Start         *time.Time            // ISO-8601 lower bound
	End           *time.Time            // ISO-8601 upper bound
	CalendarIDs   map[Provider][]string // provider → calendar IDs; JSON-encoded as a single query param
	PageSize      *int                  // capped at 1000 server-side
	NextPageToken string                // pagination cursor
	SingleEvents  *bool                 // expand recurring series
}

// EventsListResponse is the paginated response from Events.List.
type EventsListResponse struct {
	Events        []CalendarEvent `json:"events"`
	PageSize      *int            `json:"pageSize,omitempty"`
	NextPageToken string          `json:"nextPageToken,omitempty"`
}

// EventCreateData is the body for Events.Create.
type EventCreateData struct {
	Provider     Provider        `json:"provider"`
	CalendarID   string          `json:"calendarId"`
	Title        string          `json:"title"`
	Start        time.Time       `json:"start"`
	End          time.Time       `json:"end"`
	Description  string          `json:"description,omitempty"`
	Location     string          `json:"location,omitempty"`
	AllDay       *bool           `json:"allDay,omitempty"`
	Attendees    []string        `json:"attendees,omitempty"`
	Recurrence   *RecurrenceRule `json:"recurrence,omitempty"`
	Custom       map[string]any  `json:"custom,omitempty"`
	Availability string          `json:"availability,omitempty"`
	Privacy      string          `json:"privacy,omitempty"`
	Status       string          `json:"status,omitempty"`
}

// EventUpdateData is the body for Events.Update. EventID is required; all
// other fields are optional patches.
type EventUpdateData struct {
	Provider         Provider        `json:"provider"`
	CalendarID       string          `json:"calendarId"`
	EventID          string          `json:"eventId"`
	RecurringEventID string          `json:"recurringEventId,omitempty"`
	UpdateMode       string          `json:"updateMode,omitempty"` // "this" | "following" | "all"
	Title            string          `json:"title,omitempty"`
	Description      string          `json:"description,omitempty"`
	Location         string          `json:"location,omitempty"`
	Start            *time.Time      `json:"start,omitempty"`
	End              *time.Time      `json:"end,omitempty"`
	AllDay           *bool           `json:"allDay,omitempty"`
	Attendees        []string        `json:"attendees,omitempty"`
	Recurrence       *RecurrenceRule `json:"recurrence,omitempty"`
	Custom           map[string]any  `json:"custom,omitempty"`
	Availability     string          `json:"availability,omitempty"`
	Privacy          string          `json:"privacy,omitempty"`
	Status           string          `json:"status,omitempty"`
}

// EventDeleteParams is the input to Events.Delete.
type EventDeleteParams struct {
	Provider         Provider
	CalendarID       string
	EventID          string
	RecurringEventID string // optional
	DeleteMode       string // optional: "this" | "following" | "all"
}
