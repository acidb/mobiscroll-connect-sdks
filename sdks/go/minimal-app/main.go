// Minimal demo app for the Mobiscroll Connect Go SDK. Mirrors the .NET
// minimal app (samples/MinimalApp) page-for-page.
//
// Run with:
//
//	MOBISCROLL_CLIENT_ID=... \
//	MOBISCROLL_CLIENT_SECRET=... \
//	MOBISCROLL_REDIRECT_URI=http://localhost:8080/oauth/callback \
//	go run .
//
// Then open http://localhost:8080/.
package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"html"
	"io"
	"log"
	"net/http"
	"net/url"
	"os"
	"strconv"
	"strings"
	"time"

	mobiscroll "github.com/acidb/mobiscroll-connect-sdks/sdks/go"
)

const (
	accessCookie  = "access_token"
	refreshCookie = "refresh_token"
)

type appConfig struct {
	clientID     string
	clientSecret string
	redirectURI  string
	addr         string
}

func main() {
	cfg := mustConfig()
	srv := &server{cfg: cfg}
	mux := http.NewServeMux()

	mux.HandleFunc("/", srv.home)
	mux.HandleFunc("/oauth/callback", srv.callback)
	mux.HandleFunc("/callback", srv.callback) // matches .NET default
	mux.HandleFunc("/logout", srv.logout)
	mux.HandleFunc("/calendars", srv.calendars)
	mux.HandleFunc("/events", srv.events)
	mux.HandleFunc("/event-edit", srv.eventEdit)
	mux.HandleFunc("/api/events", srv.apiEvents)
	// Serve static files (app.css, event-edit.js) from the repo's static/ dir
	// at the root path so paths match the .NET app (/app.css, /event-edit.js).
	mux.Handle("/app.css", http.FileServer(http.Dir("static")))
	mux.Handle("/event-edit.js", http.FileServer(http.Dir("static")))

	log.Printf("listening on http://localhost%s", cfg.addr)
	if err := http.ListenAndServe(cfg.addr, mux); err != nil {
		log.Fatal(err)
	}
}

func mustConfig() appConfig {
	id := os.Getenv("MOBISCROLL_CLIENT_ID")
	secret := os.Getenv("MOBISCROLL_CLIENT_SECRET")
	redir := os.Getenv("MOBISCROLL_REDIRECT_URI")
	if id == "" || secret == "" || redir == "" {
		log.Fatal("MOBISCROLL_CLIENT_ID, MOBISCROLL_CLIENT_SECRET, and MOBISCROLL_REDIRECT_URI must be set")
	}
	addr := os.Getenv("PORT")
	if addr == "" {
		addr = ":8080"
	} else if !strings.HasPrefix(addr, ":") {
		addr = ":" + addr
	}
	return appConfig{clientID: id, clientSecret: secret, redirectURI: redir, addr: addr}
}

type server struct{ cfg appConfig }

// newClient builds a per-request SDK client wired to write any refreshed
// tokens back to cookies on the active response.
func (s *server) newClient(w http.ResponseWriter, r *http.Request) *mobiscroll.Client {
	c := mobiscroll.NewClient(s.cfg.clientID, s.cfg.clientSecret, s.cfg.redirectURI)
	if tok := readTokens(r); tok != nil {
		c.SetCredentials(tok)
	}
	c.OnTokensRefreshed(func(t *mobiscroll.TokenResponse) { writeTokens(w, t) })
	return c
}

// ── Home ─────────────────────────────────────────────────────────────────────

func (s *server) home(w http.ResponseWriter, r *http.Request) {
	if r.URL.Path != "/" {
		http.NotFound(w, r)
		return
	}
	tok := readTokens(r)
	c := mobiscroll.NewClient(s.cfg.clientID, s.cfg.clientSecret, s.cfg.redirectURI)
	authURL := c.Auth().GenerateAuthURL(&mobiscroll.AuthURLParams{
		UserID: "demo-user",
		Lng:    r.URL.Query().Get("lng"), // optional: try /?lng=es | fr | ar
		Providers: []mobiscroll.Provider{
			mobiscroll.ProviderGoogle, mobiscroll.ProviderMicrosoft,
			mobiscroll.ProviderApple, mobiscroll.ProviderCalDav,
		},
	})

	var body string
	if tok == nil {
		body = `<div class="alert alert-info">
  <strong>Not connected.</strong> Connect a calendar account to get started.
</div>
<a href="` + html.EscapeString(authURL) + `" class="btn btn-primary">Connect calendar account</a>`
	} else {
		body = `<div class="alert alert-success">
  <strong>Connected!</strong> You have an active session.
</div>
<p>
  <a href="/calendars" class="btn btn-primary" style="margin-right:.5rem">View Calendars</a>
  <a href="/events" class="btn btn-primary" style="margin-right:.5rem">View Events</a>
  <a href="/event-edit" class="btn btn-primary" style="margin-right:.5rem">Create / Edit Event</a>
  <a href="` + html.EscapeString(authURL) + `" class="btn btn-primary">Connect another calendar</a>
</p>`
	}
	renderPage(w, "Home", "home", body, tok != nil)
}

// ── OAuth callback ───────────────────────────────────────────────────────────

func (s *server) callback(w http.ResponseWriter, r *http.Request) {
	if errParam := r.URL.Query().Get("error"); errParam != "" {
		renderPage(w, "Error", "", `<div class="alert alert-error">OAuth error: `+html.EscapeString(errParam)+`</div>`, false)
		return
	}
	code := r.URL.Query().Get("code")
	if code == "" {
		http.Error(w, "Missing ?code", http.StatusBadRequest)
		return
	}
	c := mobiscroll.NewClient(s.cfg.clientID, s.cfg.clientSecret, s.cfg.redirectURI)
	tok, err := c.Auth().GetToken(r.Context(), code)
	if err != nil {
		http.Error(w, "Token exchange failed: "+err.Error(), http.StatusBadRequest)
		return
	}
	writeTokens(w, tok)
	http.Redirect(w, r, "/calendars", http.StatusFound)
}

// ── Logout ───────────────────────────────────────────────────────────────────

func (s *server) logout(w http.ResponseWriter, r *http.Request) {
	clearTokens(w)
	http.Redirect(w, r, "/", http.StatusFound)
}

// ── Calendars page ───────────────────────────────────────────────────────────

func (s *server) calendars(w http.ResponseWriter, r *http.Request) {
	if readTokens(r) == nil {
		http.Redirect(w, r, "/", http.StatusFound)
		return
	}
	c := s.newClient(w, r)
	cals, err := c.Calendars().List(r.Context())

	var sb strings.Builder
	sb.WriteString(`<h1>Calendars</h1>`)
	if err != nil {
		sb.WriteString(`<div class="alert alert-error">` + html.EscapeString(err.Error()) + `</div>`)
	}
	sb.WriteString(`<div class="events-table-wrap"><table><thead><tr><th>Provider</th><th>Title</th><th>ID</th><th>Timezone</th><th></th></tr></thead><tbody>`)
	if len(cals) == 0 {
		sb.WriteString(`<tr><td colspan="5" style="text-align:center;color:#6b7280;padding:2rem">No calendars found.</td></tr>`)
	}
	for _, cal := range cals {
		prov := strings.ToLower(string(cal.Provider))
		sb.WriteString(`<tr>`)
		fmt.Fprintf(&sb, `<td><span class="provider-badge provider-%s">%s</span></td>`, html.EscapeString(prov), html.EscapeString(prov))
		fmt.Fprintf(&sb, `<td class="event-title">%s</td>`, html.EscapeString(cal.Title))
		fmt.Fprintf(&sb, `<td><code style="font-size:.75rem">%s</code></td>`, html.EscapeString(cal.ID))
		fmt.Fprintf(&sb, `<td>%s</td>`, html.EscapeString(cal.TimeZone))
		fmt.Fprintf(&sb, `<td><a href="/event-edit?calendarId=%s&amp;provider=%s">create event</a></td>`,
			url.QueryEscape(cal.ID), url.QueryEscape(prov))
		sb.WriteString(`</tr>`)
	}
	sb.WriteString(`</tbody></table></div>`)
	renderPage(w, "Calendars", "calendars", sb.String(), true)
}

// ── Events page ──────────────────────────────────────────────────────────────

func (s *server) events(w http.ResponseWriter, r *http.Request) {
	if readTokens(r) == nil {
		http.Redirect(w, r, "/", http.StatusFound)
		return
	}
	c := s.newClient(w, r)

	q := r.URL.Query()
	now := time.Now().UTC()
	startDt := parseTimeParam(q.Get("start"), now.AddDate(0, 0, -31))
	endDt := parseTimeParam(q.Get("end"), now.AddDate(0, 3, 0))

	ps := 100
	if v := q.Get("pageSize"); v != "" {
		if n, err := strconv.Atoi(v); err == nil && n > 0 && n <= 1000 {
			ps = n
		}
	}
	single := true
	if v := q.Get("singleEvents"); v != "" {
		single = v == "true"
	}
	nextPageToken := q.Get("nextPageToken")

	resp, err := c.Events().List(r.Context(), &mobiscroll.EventListParams{
		Start:         &startDt,
		End:           &endDt,
		PageSize:      &ps,
		SingleEvents:  &single,
		NextPageToken: nextPageToken,
	})

	startVal := startDt.Format("2006-01-02T15:04")
	endVal := endDt.Format("2006-01-02T15:04")

	var sb strings.Builder
	sb.WriteString(`<h1>Events</h1>`)

	// Filter form
	sb.WriteString(`<div class="filter-form">
  <form method="get" action="/events">
    <div class="filter-row">
      <div class="filter-group">
        <label for="start">Start date</label>
        <input type="datetime-local" id="start" name="start" value="` + html.EscapeString(startVal) + `">
      </div>
      <div class="filter-group">
        <label for="end">End date</label>
        <input type="datetime-local" id="end" name="end" value="` + html.EscapeString(endVal) + `">
      </div>
      <div class="filter-group">
        <label for="pageSize">Page size</label>
        <input type="number" id="pageSize" name="pageSize" min="1" max="1000" value="` + strconv.Itoa(ps) + `">
      </div>
      <button type="submit" class="btn btn-primary">Load</button>
    </div>
    <div class="checkbox-group">
      <input type="checkbox" id="singleEvents" name="singleEvents" value="true"` + checkedAttr(single) + `>
      <label for="singleEvents">Single Events (expand recurring)</label>
    </div>
  </form>
</div>`)

	// API info
	apiURL := fmt.Sprintf("/events?start=%s&amp;end=%s&amp;pageSize=%d&amp;singleEvents=%t",
		html.EscapeString(startVal), html.EscapeString(endVal), ps, single)
	if nextPageToken != "" {
		apiURL += "&amp;nextPageToken=…"
	}
	sb.WriteString(`<div class="api-info"><span class="api-badge">GET</span><span class="api-url">` + apiURL + `</span></div>`)

	if err != nil {
		sb.WriteString(`<div class="alert alert-error">` + html.EscapeString(err.Error()) + `</div>`)
	}

	sb.WriteString(`<div class="events-table-wrap"><table><thead><tr><th>Provider</th><th>Title / Calendar</th><th>Start → End</th><th></th><th></th></tr></thead><tbody>`)
	if resp == nil || len(resp.Events) == 0 {
		sb.WriteString(`<tr><td colspan="5" style="text-align:center;color:#6b7280;padding:2rem">No events found for this range.</td></tr>`)
	} else {
		for _, e := range resp.Events {
			prov := strings.ToLower(string(e.Provider))
			startStr, endStr := "", ""
			if e.Start != nil {
				startStr = e.Start.Format("2006-01-02 15:04")
			}
			if e.End != nil {
				endStr = e.End.Format("15:04")
			}
			sb.WriteString(`<tr>`)
			fmt.Fprintf(&sb, `<td><span class="provider-badge provider-%s">%s</span></td>`, html.EscapeString(prov), html.EscapeString(prov))
			fmt.Fprintf(&sb, `<td><div class="event-title">%s</div><div class="event-meta">%s</div></td>`,
				html.EscapeString(e.Title), html.EscapeString(e.CalendarID))
			fmt.Fprintf(&sb, `<td>%s<br><span class="event-meta">→ %s</span></td>`, html.EscapeString(startStr), html.EscapeString(endStr))
			allDayMark := ""
			if e.AllDay {
				allDayMark = `<span style="color:#6b7280;font-size:.8rem">all day</span>`
			}
			fmt.Fprintf(&sb, `<td>%s</td>`, allDayMark)
			fmt.Fprintf(&sb, `<td><a href="/event-edit?eventId=%s&amp;calendarId=%s&amp;provider=%s">edit</a></td>`,
				url.QueryEscape(e.ID), url.QueryEscape(e.CalendarID), url.QueryEscape(prov))
			sb.WriteString(`</tr>`)
		}
	}
	sb.WriteString(`</tbody></table></div>`)

	// Raw JSON details
	if resp != nil && len(resp.Events) > 0 {
		raw, _ := json.MarshalIndent(resp, "", "  ")
		sb.WriteString(`<details style="margin-top:1.5rem"><summary style="cursor:pointer;font-weight:600;color:#374151">Raw API response JSON</summary><pre class="result-box">` +
			html.EscapeString(string(raw)) + `</pre></details>`)
	}

	// Pagination
	if resp != nil && resp.NextPageToken != "" {
		nextQS := fmt.Sprintf("start=%s&end=%s&pageSize=%d&singleEvents=%t&nextPageToken=%s",
			url.QueryEscape(startVal), url.QueryEscape(endVal), ps, single, url.QueryEscape(resp.NextPageToken))
		sb.WriteString(`<div class="pagination-bar"><a href="/events?` + html.EscapeString(nextQS) + `" class="btn btn-primary">Load next page →</a></div>`)
	}

	renderPage(w, "Events", "events", sb.String(), true)
}

// ── Event edit page ──────────────────────────────────────────────────────────

func (s *server) eventEdit(w http.ResponseWriter, r *http.Request) {
	if readTokens(r) == nil {
		http.Redirect(w, r, "/", http.StatusFound)
		return
	}
	c := s.newClient(w, r)
	cals, _ := c.Calendars().List(r.Context())

	q := r.URL.Query()
	eventID := q.Get("eventId")
	calendarID := q.Get("calendarId")
	provider := q.Get("provider")

	// Initial dropdown options (filtered by provider if supplied — matches .NET)
	type calOpt struct {
		ID       string `json:"id"`
		Title    string `json:"title"`
		Provider string `json:"provider"`
	}
	allOpts := make([]calOpt, 0, len(cals))
	for _, cal := range cals {
		allOpts = append(allOpts, calOpt{ID: cal.ID, Title: cal.Title, Provider: strings.ToLower(string(cal.Provider))})
	}
	calJSON, _ := json.Marshal(allOpts)

	var calOptionsHTML strings.Builder
	for _, o := range allOpts {
		if provider != "" && o.Provider != provider {
			continue
		}
		selected := ""
		if o.ID == calendarID {
			selected = " selected"
		}
		fmt.Fprintf(&calOptionsHTML, `<option value="%s"%s>%s (%s)</option>`,
			html.EscapeString(o.ID), selected, html.EscapeString(o.Title), html.EscapeString(o.Provider))
	}

	now := time.Now().UTC()
	defaultStart := now.Format("2006-01-02T15:04")
	defaultEnd := now.Add(time.Hour).Format("2006-01-02T15:04")

	providerOpt := func(value, label string) string {
		sel := ""
		if value == provider {
			sel = " selected"
		}
		return fmt.Sprintf(`<option value="%s"%s>%s</option>`, value, sel, label)
	}

	body := `<h1>Create / Edit Event</h1>

<div id="result-box" class="alert" style="display:none"></div>

<form class="event-form" id="event-form">

  <div class="form-section">
    <h3 class="section-title">Event Settings</h3>
    <div class="form-row">
      <div class="form-group">
        <label for="provider">Provider *</label>
        <select id="provider" name="provider" required>
          <option value="">Select provider…</option>
          ` + providerOpt("google", "Google") + `
          ` + providerOpt("microsoft", "Microsoft") + `
          ` + providerOpt("apple", "Apple") + `
          ` + providerOpt("caldav", "CalDAV") + `
        </select>
      </div>
      <div class="form-group">
        <label for="mode">Update / Delete Mode</label>
        <select id="mode" name="mode">
          <option value="this">This event only</option>
          <option value="following">This and following</option>
          <option value="all">All events in series</option>
        </select>
      </div>
    </div>
    <div class="form-group">
      <label for="eventId">Event ID</label>
      <input type="text" id="eventId" name="eventId" value="` + html.EscapeString(eventID) + `" placeholder="Leave blank to create a new event">
      <div class="help-text">Leave blank to create · fill in to update/delete</div>
    </div>
    <div class="form-group">
      <label for="recurringEventId">Recurring Event ID</label>
      <input type="text" id="recurringEventId" name="recurringEventId" placeholder="Only for recurring event instances">
      <div class="help-text">Only needed when editing a specific instance of a recurring event</div>
    </div>
    <div class="form-group">
      <label for="calendarId">Calendar *</label>
      <select id="calendarId" name="calendarId" required>
        <option value="">Select a calendar…</option>
        ` + calOptionsHTML.String() + `
      </select>
    </div>
  </div>

  <div class="form-section">
    <h3 class="section-title">Event Details</h3>
    <div class="form-group">
      <label for="title">Title *</label>
      <input type="text" id="title" name="title" value="Weekly Team Meeting" required>
    </div>
    <div class="form-group">
      <label for="description">Description</label>
      <textarea id="description" name="description">Discuss project updates and blockers.</textarea>
    </div>
    <div class="form-group">
      <label for="location">Location</label>
      <input type="text" id="location" name="location" placeholder="Conference Room A">
    </div>
    <div class="form-group">
      <label for="attendees">Attendees</label>
      <textarea id="attendees" name="attendees" placeholder="email1@example.com&#10;email2@example.com"></textarea>
      <div class="help-text">One email address per line</div>
    </div>
  </div>

  <div class="form-section">
    <h3 class="section-title">Date &amp; Time</h3>
    <div class="checkbox-group" style="margin-bottom:.9rem">
      <input type="checkbox" id="allDay" name="allDay">
      <label for="allDay">All-day event</label>
    </div>
    <div class="form-row">
      <div class="form-group">
        <label for="startDate">Start *</label>
        <input type="datetime-local" id="startDate" name="startDate" value="` + defaultStart + `" required>
      </div>
      <div class="form-group">
        <label for="endDate">End *</label>
        <input type="datetime-local" id="endDate" name="endDate" value="` + defaultEnd + `" required>
      </div>
    </div>
  </div>

  <div class="form-section">
    <div class="recurrence-toggle" onclick="toggleRecurrence()">
      <input type="checkbox" id="recurrenceEnabled" name="recurrenceEnabled" onclick="event.stopPropagation();toggleRecurrence()">
      <label for="recurrenceEnabled">Add Recurrence</label>
    </div>
    <div id="recurrenceContent" class="recurrence-content">
      <div class="form-row">
        <div class="form-group">
          <label for="frequency">Frequency</label>
          <select id="frequency" name="frequency">
            <option value="DAILY">Daily</option>
            <option value="WEEKLY">Weekly</option>
            <option value="MONTHLY">Monthly</option>
            <option value="YEARLY">Yearly</option>
          </select>
        </div>
        <div class="form-group">
          <label for="interval">Interval</label>
          <input type="number" id="interval" name="interval" value="1" min="1">
          <div class="help-text">Repeat every N days/weeks/months</div>
        </div>
      </div>
      <div class="form-group">
        <label for="count">Count</label>
        <input type="number" id="count" name="count" value="3" min="1">
        <div class="help-text">Number of occurrences</div>
      </div>
      <div class="form-group">
        <label for="byDay">By Day</label>
        <input type="text" id="byDay" name="byDay" value="MO,TU,WE,TH,FR,SA,SU" placeholder="MO,TU,WE,TH,FR">
        <div class="help-text">Comma-separated: SU,MO,TU,WE,TH,FR,SA</div>
      </div>
    </div>
  </div>

  <div class="btn-group">
    <button type="submit" class="btn btn-primary" id="submitBtn">Create / Update Event</button>
    <button type="button" class="btn btn-danger" id="deleteBtn" onclick="handleDelete()">Delete Event</button>
  </div>

</form>

<script>window.CAL_DATA = ` + string(calJSON) + `;</script>
<script src="/event-edit.js"></script>`

	renderPage(w, "Event Edit", "event-edit", body, true)
}

// ── API: events CRUD (JSON) ──────────────────────────────────────────────────

type recurrenceBody struct {
	Frequency string   `json:"frequency"`
	Interval  *int     `json:"interval"`
	Count     *int     `json:"count"`
	ByDay     []string `json:"byDay"`
}

type eventBody struct {
	Provider         string          `json:"provider"`
	CalendarID       string          `json:"calendarId"`
	EventID          string          `json:"eventId"`
	RecurringEventID string          `json:"recurringEventId"`
	Title            string          `json:"title"`
	Description      string          `json:"description"`
	Location         string          `json:"location"`
	Start            string          `json:"start"`
	End              string          `json:"end"`
	AllDay           *bool           `json:"allDay"`
	Attendees        []string        `json:"attendees"`
	UpdateMode       string          `json:"updateMode"`
	DeleteMode       string          `json:"deleteMode"`
	Recurrence       *recurrenceBody `json:"recurrence"`
}

func (s *server) apiEvents(w http.ResponseWriter, r *http.Request) {
	if readTokens(r) == nil {
		writeJSON(w, http.StatusUnauthorized, map[string]string{"error": "Not authenticated"})
		return
	}
	var body eventBody
	if r.Method != http.MethodGet {
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil && !errors.Is(err, io.EOF) {
			writeJSON(w, http.StatusBadRequest, map[string]string{"error": err.Error()})
			return
		}
	}
	c := s.newClient(w, r)
	ctx, cancel := context.WithTimeout(r.Context(), 30*time.Second)
	defer cancel()

	switch r.Method {
	case http.MethodPost:
		start, _ := time.Parse(time.RFC3339, body.Start)
		end, _ := time.Parse(time.RFC3339, body.End)
		data := &mobiscroll.EventCreateData{
			Provider:    mobiscroll.Provider(body.Provider),
			CalendarID:  body.CalendarID,
			Title:       body.Title,
			Description: body.Description,
			Location:    body.Location,
			Start:       start,
			End:         end,
			AllDay:      body.AllDay,
			Attendees:   body.Attendees,
			Recurrence:  toRecurrence(body.Recurrence),
		}
		ev, err := c.Events().Create(ctx, data)
		respondMutation(w, ev, err)
	case http.MethodPut:
		data := &mobiscroll.EventUpdateData{
			Provider:         mobiscroll.Provider(body.Provider),
			CalendarID:       body.CalendarID,
			EventID:          body.EventID,
			RecurringEventID: body.RecurringEventID,
			UpdateMode:       body.UpdateMode,
			Title:            body.Title,
			Description:      body.Description,
			Location:         body.Location,
			AllDay:           body.AllDay,
			Attendees:        body.Attendees,
			Recurrence:       toRecurrence(body.Recurrence),
		}
		if body.Start != "" {
			if t, err := time.Parse(time.RFC3339, body.Start); err == nil {
				data.Start = &t
			}
		}
		if body.End != "" {
			if t, err := time.Parse(time.RFC3339, body.End); err == nil {
				data.End = &t
			}
		}
		ev, err := c.Events().Update(ctx, data)
		respondMutation(w, ev, err)
	case http.MethodDelete:
		err := c.Events().Delete(ctx, &mobiscroll.EventDeleteParams{
			Provider:         mobiscroll.Provider(body.Provider),
			CalendarID:       body.CalendarID,
			EventID:          body.EventID,
			RecurringEventID: body.RecurringEventID,
			DeleteMode:       body.DeleteMode,
		})
		if err != nil {
			writeJSON(w, http.StatusBadRequest, map[string]string{"error": err.Error()})
			return
		}
		writeJSON(w, http.StatusOK, map[string]bool{"success": true})
	default:
		w.Header().Set("Allow", "POST, PUT, DELETE")
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
	}
}

func toRecurrence(r *recurrenceBody) *mobiscroll.RecurrenceRule {
	if r == nil || r.Frequency == "" {
		return nil
	}
	return &mobiscroll.RecurrenceRule{
		Frequency: r.Frequency,
		Interval:  r.Interval,
		Count:     r.Count,
		ByDay:     r.ByDay,
	}
}

func respondMutation(w http.ResponseWriter, ev *mobiscroll.CalendarEvent, err error) {
	if err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": err.Error()})
		return
	}
	writeJSON(w, http.StatusOK, ev)
}

// ── Helpers ──────────────────────────────────────────────────────────────────

func readTokens(r *http.Request) *mobiscroll.TokenResponse {
	at, err := r.Cookie(accessCookie)
	if err != nil || at.Value == "" {
		return nil
	}
	rt, _ := r.Cookie(refreshCookie)
	tok := &mobiscroll.TokenResponse{AccessToken: at.Value, TokenType: "Bearer"}
	if rt != nil {
		tok.RefreshToken = rt.Value
	}
	return tok
}

func writeTokens(w http.ResponseWriter, t *mobiscroll.TokenResponse) {
	if t.AccessToken != "" {
		http.SetCookie(w, &http.Cookie{
			Name: accessCookie, Value: t.AccessToken, Path: "/", HttpOnly: true,
			MaxAge: 7 * 24 * 60 * 60, SameSite: http.SameSiteLaxMode,
		})
	}
	if t.RefreshToken != "" {
		http.SetCookie(w, &http.Cookie{
			Name: refreshCookie, Value: t.RefreshToken, Path: "/", HttpOnly: true,
			MaxAge: 30 * 24 * 60 * 60, SameSite: http.SameSiteLaxMode,
		})
	}
}

func clearTokens(w http.ResponseWriter) {
	for _, name := range []string{accessCookie, refreshCookie} {
		http.SetCookie(w, &http.Cookie{Name: name, Value: "", Path: "/", MaxAge: -1})
	}
}

func parseTimeParam(v string, def time.Time) time.Time {
	if v == "" {
		return def
	}
	if t, err := time.Parse(time.RFC3339, v); err == nil {
		return t
	}
	if t, err := time.Parse("2006-01-02T15:04", v); err == nil {
		return t
	}
	return def
}

func checkedAttr(b bool) string {
	if b {
		return " checked"
	}
	return ""
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

func renderPage(w http.ResponseWriter, title, active, body string, loggedIn bool) {
	navLink := func(href, label, slug string) string {
		cls := ""
		if slug == active {
			cls = `class="active"`
		}
		return fmt.Sprintf(`<a href="%s" %s>%s</a>`, href, cls, html.EscapeString(label))
	}
	nav := navLink("/", "Home", "home") +
		navLink("/calendars", "Calendars", "calendars") +
		navLink("/events", "Events", "events") +
		navLink("/event-edit", "Event Edit", "event-edit")
	if loggedIn {
		nav += `<a href="/logout" style="background:#dc3545">Logout</a>`
	}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	fmt.Fprintf(w, `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>%s — Mobiscroll Connect Go</title>
  <link rel="stylesheet" href="/app.css">
</head>
<body>
  <header class="nav-header">
    <h2>Mobiscroll Connect — Go SDK demo</h2>
    <nav class="nav-menu">%s</nav>
  </header>
  <main class="page">%s</main>
</body>
</html>`, html.EscapeString(title), nav, body)
}
