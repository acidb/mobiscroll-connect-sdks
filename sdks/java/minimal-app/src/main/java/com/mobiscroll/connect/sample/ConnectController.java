package com.mobiscroll.connect.sample;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mobiscroll.connect.MobiscrollConnectClient;
import com.mobiscroll.connect.exceptions.MobiscrollConnectException;
import com.mobiscroll.connect.models.AuthUrlParams;
import com.mobiscroll.connect.models.Calendar;
import com.mobiscroll.connect.models.CalendarEvent;
import com.mobiscroll.connect.models.EventCreateData;
import com.mobiscroll.connect.models.EventDeleteParams;
import com.mobiscroll.connect.models.EventListParams;
import com.mobiscroll.connect.models.EventUpdateData;
import com.mobiscroll.connect.models.EventsListResponse;
import com.mobiscroll.connect.models.TokenResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
public class ConnectController {

    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final MobiscrollConnectClient client;

    public ConnectController(MobiscrollConnectClient client) {
        this.client = client;
    }

    // ── Token helpers (httpOnly cookies) ───────────────────────────────────────

    private static TokenResponse readTokens(HttpServletRequest req) {
        if (req.getCookies() == null) return null;
        String at = null, rt = null;
        for (Cookie c : req.getCookies()) {
            if ("access_token".equals(c.getName()))  at = c.getValue();
            if ("refresh_token".equals(c.getName())) rt = c.getValue();
        }
        if ((at == null || at.isEmpty()) && (rt == null || rt.isEmpty())) return null;
        return new TokenResponse(at != null ? at : "", "Bearer", null, rt, null);
    }

    private static void writeTokens(HttpServletResponse res, TokenResponse tokens) {
        if (tokens.getAccessToken() != null && !tokens.getAccessToken().isEmpty()) {
            Cookie at = new Cookie("access_token", tokens.getAccessToken());
            at.setHttpOnly(true); at.setPath("/"); at.setMaxAge(7 * 24 * 3600);
            res.addCookie(at);
        }
        if (tokens.getRefreshToken() != null && !tokens.getRefreshToken().isEmpty()) {
            Cookie rt = new Cookie("refresh_token", tokens.getRefreshToken());
            rt.setHttpOnly(true); rt.setPath("/"); rt.setMaxAge(30 * 24 * 3600);
            res.addCookie(rt);
        }
    }

    private static void clearTokens(HttpServletResponse res) {
        Cookie at = new Cookie("access_token", "");
        at.setHttpOnly(true); at.setPath("/"); at.setMaxAge(0);
        res.addCookie(at);
        Cookie rt = new Cookie("refresh_token", "");
        rt.setHttpOnly(true); rt.setPath("/"); rt.setMaxAge(0);
        res.addCookie(rt);
    }

    private void bind(TokenResponse tokens, HttpServletResponse res) {
        client.setCredentials(tokens);
        client.onTokensRefreshed(t -> writeTokens(res, t));
    }

    private static String h(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String enc(String s) {
        return URLEncoder.encode(s != null ? s : "", StandardCharsets.UTF_8);
    }

    private static String layout(String title, String activePage, String body, boolean loggedIn) {
        return "<!doctype html>\n<html lang=\"en\">\n<head>\n"
                + "  <meta charset=\"utf-8\">\n"
                + "  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n"
                + "  <title>" + h(title) + " — Mobiscroll Connect Java</title>\n"
                + "  <link rel=\"stylesheet\" href=\"/app.css\">\n"
                + "</head>\n<body>\n"
                + "  <header class=\"nav-header\">\n"
                + "    <h2>Mobiscroll Connect — Java SDK demo</h2>\n"
                + "    <nav class=\"nav-menu\">\n"
                + "      <a href=\"/\" class=\"" + ("home".equals(activePage) ? "active" : "") + "\">Home</a>\n"
                + "      <a href=\"/calendars\" class=\"" + ("calendars".equals(activePage) ? "active" : "") + "\">Calendars</a>\n"
                + "      <a href=\"/events\" class=\"" + ("events".equals(activePage) ? "active" : "") + "\">Events</a>\n"
                + "      <a href=\"/event-edit\" class=\"" + ("event-edit".equals(activePage) ? "active" : "") + "\">Event Edit</a>\n"
                + (loggedIn ? "      <a href=\"/logout\" style=\"background:#dc3545\">Logout</a>\n" : "")
                + "    </nav>\n"
                + "  </header>\n"
                + "  <main class=\"page\">\n"
                + body + "\n"
                + "  </main>\n"
                + "</body>\n</html>";
    }

    // ── HOME ────────────────────────────────────────────────────────────────────

    @GetMapping(value = "/", produces = "text/html")
    public String home(HttpServletRequest req) {
        TokenResponse tokens = readTokens(req);
        String authUrl = client.auth().generateAuthUrl(AuthUrlParams.builder()
                .userId("demo-user")
                .providers(Arrays.asList(
                        com.mobiscroll.connect.Provider.GOOGLE,
                        com.mobiscroll.connect.Provider.MICROSOFT,
                        com.mobiscroll.connect.Provider.APPLE,
                        com.mobiscroll.connect.Provider.CALDAV))
                .build());

        String body;
        if (tokens == null) {
            body = "<div class=\"alert alert-info\">"
                    + "<strong>Not connected.</strong> Connect a calendar account to get started."
                    + "</div>\n"
                    + "<a href=\"" + h(authUrl) + "\" class=\"btn btn-primary\">Connect calendar account</a>";
        } else {
            body = "<div class=\"alert alert-success\">"
                    + "<strong>Connected!</strong> You have an active session."
                    + "</div>\n"
                    + "<p style=\"margin-bottom:1.5rem\">"
                    + "<a href=\"/calendars\" class=\"btn btn-primary\" style=\"margin-right:.5rem\">View Calendars</a>"
                    + "<a href=\"/events\" class=\"btn btn-primary\" style=\"margin-right:.5rem\">View Events</a>"
                    + "<a href=\"/event-edit\" class=\"btn btn-primary\">Create / Edit Event</a>"
                    + "</p>"
                    + "<hr style=\"margin:1.5rem 0;border:none;border-top:1px solid #e5e7eb\">\n"
                    + "<p style=\"margin-bottom:.75rem;color:#374151;font-weight:500\">Connect another calendar account:</p>\n"
                    + "<a href=\"" + h(authUrl) + "\" class=\"btn btn-primary\">Connect calendar account</a>";
        }
        return layout("Home", "home", body, tokens != null);
    }

    // ── CALLBACK ────────────────────────────────────────────────────────────────

    @GetMapping("/oauth/callback")
    public Object callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            HttpServletResponse res) {
        if (error != null && !error.isEmpty()) {
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.TEXT_HTML)
                    .body(layout("Error", "", "<div class=\"alert alert-error\">OAuth error: " + h(error) + "</div>", false));
        }
        if (code == null || code.isEmpty()) {
            return ResponseEntity.badRequest().body("Missing ?code");
        }
        TokenResponse tokens = client.auth().getToken(code);
        writeTokens(res, tokens);
        RedirectView rv = new RedirectView("/calendars");
        rv.setStatusCode(org.springframework.http.HttpStatus.FOUND);
        return rv;
    }

    // ── LOGOUT ──────────────────────────────────────────────────────────────────

    @GetMapping("/logout")
    public RedirectView logout(HttpServletResponse res) {
        clearTokens(res);
        return new RedirectView("/");
    }

    // ── CALENDARS PAGE ──────────────────────────────────────────────────────────

    @GetMapping(value = "/calendars", produces = "text/html")
    public Object calendars(HttpServletRequest req, HttpServletResponse res) {
        TokenResponse tokens = readTokens(req);
        if (tokens == null) {
            RedirectView rv = new RedirectView("/");
            rv.setStatusCode(org.springframework.http.HttpStatus.FOUND);
            return rv;
        }
        bind(tokens, res);

        List<Calendar> calendars = null;
        String errorMsg = null;
        try {
            calendars = client.calendars().list();
        } catch (Exception ex) {
            errorMsg = ex.getMessage();
        }

        StringBuilder rows = new StringBuilder();
        if (calendars != null && !calendars.isEmpty()) {
            for (Calendar c : calendars) {
                String prov = c.getProvider() != null ? c.getProvider().toString().toLowerCase() : "";
                rows.append("<tr>")
                    .append("<td><span class=\"provider-badge provider-").append(h(prov)).append("\">").append(h(prov)).append("</span></td>")
                    .append("<td class=\"event-title\">").append(h(calendarDisplayName(c))).append("</td>")
                    .append("<td><code style=\"font-size:.75rem\">").append(h(c.getId())).append("</code></td>")
                    .append("<td>").append(h(c.getTimeZone())).append("</td>")
                    .append("<td><a href=\"/event-edit?calendarId=").append(enc(c.getId())).append("&amp;provider=").append(enc(prov)).append("\">create event</a></td>")
                    .append("</tr>\n");
            }
        } else {
            rows.append("<tr><td colspan=\"5\" style=\"text-align:center;color:#6b7280;padding:2rem\">No calendars found.</td></tr>");
        }

        String body = "<h1>Calendars</h1>\n"
                + (errorMsg != null ? "<div class=\"alert alert-error\">" + h(errorMsg) + "</div>\n" : "")
                + "<div class=\"events-table-wrap\">\n"
                + "  <table>\n"
                + "    <thead><tr><th>Provider</th><th>Title</th><th>ID</th><th>Timezone</th><th></th></tr></thead>\n"
                + "    <tbody>" + rows + "</tbody>\n"
                + "  </table>\n"
                + "</div>";

        return layout("Calendars", "calendars", body, true);
    }

    // ── EVENTS PAGE ─────────────────────────────────────────────────────────────

    @GetMapping(value = "/events", produces = "text/html")
    public Object events(
            HttpServletRequest req, HttpServletResponse res,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false, defaultValue = "100") int pageSize,
            @RequestParam(required = false, defaultValue = "true") boolean singleEvents,
            @RequestParam(required = false) String nextPageToken) {

        TokenResponse tokens = readTokens(req);
        if (tokens == null) {
            RedirectView rv = new RedirectView("/");
            rv.setStatusCode(org.springframework.http.HttpStatus.FOUND);
            return rv;
        }
        bind(tokens, res);

        OffsetDateTime now = OffsetDateTime.now(java.time.ZoneOffset.UTC);
        OffsetDateTime startDt = parseOrDefault(start, now.minusDays(31));
        OffsetDateTime endDt   = parseOrDefault(end,   now.plusDays(90));
        int ps = Math.max(1, Math.min(1000, pageSize));

        EventsListResponse result = null;
        String errorMsg = null;
        try {
            result = client.events().list(EventListParams.builder()
                    .start(startDt).end(endDt)
                    .pageSize(ps)
                    .singleEvents(singleEvents)
                    .nextPageToken(nextPageToken)
                    .build());
        } catch (Exception ex) {
            errorMsg = ex.getMessage();
        }

        String startVal = startDt.toString().substring(0, 16);
        String endVal   = endDt.toString().substring(0, 16);

        StringBuilder rows = new StringBuilder();
        if (result != null && result.getEvents() != null && !result.getEvents().isEmpty()) {
            for (CalendarEvent e : result.getEvents()) {
                String prov = e.getProvider() != null ? e.getProvider().toString().toLowerCase() : "";
                String startStr = e.getStart() != null ? e.getStart().toString().replace("T", " ").substring(0, 16) : "";
                String endStr   = e.getEnd()   != null ? e.getEnd().toString().substring(11, 16) : "";
                rows.append("<tr>")
                    .append("<td><span class=\"provider-badge provider-").append(h(prov)).append("\">").append(h(prov)).append("</span></td>")
                    .append("<td><div class=\"event-title\">").append(h(e.getTitle())).append("</div>")
                    .append("<div class=\"event-meta\">").append(h(e.getCalendarId())).append("</div></td>")
                    .append("<td>").append(h(startStr)).append("<br><span class=\"event-meta\">→ ").append(h(endStr)).append("</span></td>")
                    .append("<td>").append(Boolean.TRUE.equals(e.getAllDay()) ? "<span style='color:#6b7280;font-size:.8rem'>all day</span>" : "").append("</td>")
                    .append("<td><a href=\"/event-edit?eventId=").append(enc(e.getId()))
                    .append("&amp;calendarId=").append(enc(e.getCalendarId()))
                    .append("&amp;provider=").append(enc(prov)).append("\">edit</a></td>")
                    .append("</tr>\n");
            }
        } else {
            rows.append("<tr><td colspan=\"5\" style=\"text-align:center;color:#6b7280;padding:2rem\">No events found for this range.</td></tr>");
        }

        String nextPageQs = (result != null && result.getNextPageToken() != null && !result.getNextPageToken().isEmpty())
                ? "start=" + enc(startVal) + "&end=" + enc(endVal) + "&pageSize=" + ps + "&singleEvents=" + singleEvents + "&nextPageToken=" + enc(result.getNextPageToken())
                : null;

        String eventsJson = "";
        if (result != null) {
            try { eventsJson = JSON.writeValueAsString(result); } catch (Exception ignored) {}
        }

        String body = "<h1>Events</h1>\n"

                + "<div class=\"filter-form\">\n"
                + "  <form method=\"get\" action=\"/events\">\n"
                + "    <div class=\"filter-row\">\n"
                + "      <div class=\"filter-group\"><label for=\"start\">Start date</label>"
                + "<input type=\"datetime-local\" id=\"start\" name=\"start\" value=\"" + h(startVal) + "\"></div>\n"
                + "      <div class=\"filter-group\"><label for=\"end\">End date</label>"
                + "<input type=\"datetime-local\" id=\"end\" name=\"end\" value=\"" + h(endVal) + "\"></div>\n"
                + "      <div class=\"filter-group\"><label for=\"pageSize\">Page size</label>"
                + "<input type=\"number\" id=\"pageSize\" name=\"pageSize\" min=\"1\" max=\"1000\" value=\"" + ps + "\"></div>\n"
                + "      <button type=\"submit\" class=\"btn btn-primary\">Load</button>\n"
                + "    </div>\n"
                + "    <div class=\"checkbox-group\">\n"
                + "      <input type=\"checkbox\" id=\"singleEvents\" name=\"singleEvents\" value=\"true\" " + (singleEvents ? "checked" : "") + ">\n"
                + "      <label for=\"singleEvents\">Single Events (expand recurring)</label>\n"
                + "    </div>\n"
                + "  </form>\n"
                + "</div>\n"

                + "<div class=\"api-info\">"
                + "<span class=\"api-badge\">GET</span>"
                + "<span class=\"api-url\">/events?start=" + h(startVal) + "&amp;end=" + h(endVal) + "&amp;pageSize=" + ps + "&amp;singleEvents=" + singleEvents
                + (nextPageToken != null ? "&amp;nextPageToken=…" : "") + "</span>"
                + "</div>\n"

                + (errorMsg != null ? "<div class=\"alert alert-error\">" + h(errorMsg) + "</div>\n" : "")

                + "<div class=\"events-table-wrap\">\n"
                + "  <table>\n"
                + "    <thead><tr><th>Provider</th><th>Title / Calendar</th><th>Start → End</th><th></th><th></th></tr></thead>\n"
                + "    <tbody>" + rows + "</tbody>\n"
                + "  </table>\n"
                + "</div>\n"

                + (result != null && result.getEvents() != null && !result.getEvents().isEmpty()
                ? "<details style=\"margin-top:1.5rem\">"
                + "<summary style=\"cursor:pointer;font-weight:600;color:#374151\">Raw API response JSON</summary>"
                + "<pre class=\"result-box\">" + h(eventsJson) + "</pre>"
                + "</details>\n" : "")

                + (nextPageQs != null
                ? "<div class=\"pagination-bar\"><a href=\"/events?" + h(nextPageQs) + "\" class=\"btn btn-primary\">Load next page →</a></div>\n"
                : "");

        return layout("Events", "events", body, true);
    }

    // ── EVENT EDIT PAGE ─────────────────────────────────────────────────────────

    @GetMapping(value = "/event-edit", produces = "text/html")
    public Object eventEdit(
            HttpServletRequest req, HttpServletResponse res,
            @RequestParam(required = false) String eventId,
            @RequestParam(required = false) String calendarId,
            @RequestParam(required = false) String provider) {

        TokenResponse tokens = readTokens(req);
        if (tokens == null) {
            RedirectView rv = new RedirectView("/");
            rv.setStatusCode(org.springframework.http.HttpStatus.FOUND);
            return rv;
        }
        bind(tokens, res);

        List<Calendar> calendars = List.of();
        try { calendars = client.calendars().list(); } catch (Exception ignored) {}

        OffsetDateTime now = OffsetDateTime.now(java.time.ZoneOffset.UTC);
        String defaultStart = now.toString().substring(0, 16);
        String defaultEnd   = now.plusHours(1).toString().substring(0, 16);

        // Render all calendars; JS filters by provider on load and on change.
        StringBuilder calOptions = new StringBuilder();
        for (Calendar c : calendars) {
            String cp = c.getProvider() != null ? c.getProvider().toString().toLowerCase() : "";
            calOptions.append("<option value=\"").append(h(c.getId())).append("\"")
                    .append(c.getId() != null && c.getId().equals(calendarId) ? " selected" : "").append(">")
                    .append(h(calendarDisplayName(c))).append(" (").append(h(cp)).append(")</option>\n");
        }

        String calJson;
        try {
            List<Map<String, String>> calList = new java.util.ArrayList<>();
            for (Calendar c : calendars) {
                String cp = c.getProvider() != null ? c.getProvider().toString().toLowerCase() : "";
                calList.add(Map.of("id", c.getId() != null ? c.getId() : "", "title", calendarDisplayName(c), "provider", cp));
            }
            calJson = JSON.writeValueAsString(calList);
        } catch (Exception e) {
            calJson = "[]";
        }

        String body = "<h1>Create / Edit Event</h1>\n"
                + "<div id=\"result-box\" class=\"alert\" style=\"display:none\"></div>\n"
                + "<form class=\"event-form\" id=\"event-form\">\n"

                + "  <div class=\"form-section\">\n"
                + "    <h3 class=\"section-title\">Event Settings</h3>\n"
                + "    <div class=\"form-row\">\n"
                + "      <div class=\"form-group\">\n"
                + "        <label for=\"provider\">Provider *</label>\n"
                + "        <select id=\"provider\" name=\"provider\" required>\n"
                + "          <option value=\"\">Select provider…</option>\n"
                + "          <option value=\"google\""    + ("google".equals(provider)    ? " selected" : "") + ">Google</option>\n"
                + "          <option value=\"microsoft\"" + ("microsoft".equals(provider) ? " selected" : "") + ">Microsoft</option>\n"
                + "          <option value=\"apple\""     + ("apple".equals(provider)     ? " selected" : "") + ">Apple</option>\n"
                + "          <option value=\"caldav\""    + ("caldav".equals(provider)    ? " selected" : "") + ">CalDAV</option>\n"
                + "        </select>\n"
                + "      </div>\n"
                + "      <div class=\"form-group\">\n"
                + "        <label for=\"mode\">Update / Delete Mode</label>\n"
                + "        <select id=\"mode\" name=\"mode\">\n"
                + "          <option value=\"this\">This event only</option>\n"
                + "          <option value=\"following\">This and following</option>\n"
                + "          <option value=\"all\">All events in series</option>\n"
                + "        </select>\n"
                + "      </div>\n"
                + "    </div>\n"
                + "    <div class=\"form-group\">\n"
                + "      <label for=\"eventId\">Event ID</label>\n"
                + "      <input type=\"text\" id=\"eventId\" name=\"eventId\" value=\"" + h(eventId != null ? eventId : "") + "\" placeholder=\"Leave blank to create a new event\">\n"
                + "      <div class=\"help-text\">Leave blank to create · fill in to update/delete</div>\n"
                + "    </div>\n"
                + "    <div class=\"form-group\">\n"
                + "      <label for=\"recurringEventId\">Recurring Event ID</label>\n"
                + "      <input type=\"text\" id=\"recurringEventId\" name=\"recurringEventId\" placeholder=\"Only for recurring event instances\">\n"
                + "      <div class=\"help-text\">Only needed when editing a specific instance of a recurring event</div>\n"
                + "    </div>\n"
                + "    <div class=\"form-group\">\n"
                + "      <label for=\"calendarId\">Calendar *</label>\n"
                + "      <select id=\"calendarId\" name=\"calendarId\" required>\n"
                + "        <option value=\"\">Select a calendar…</option>\n"
                + calOptions
                + "      </select>\n"
                + "    </div>\n"
                + "  </div>\n"

                + "  <div class=\"form-section\">\n"
                + "    <h3 class=\"section-title\">Event Details</h3>\n"
                + "    <div class=\"form-group\">\n"
                + "      <label for=\"title\">Title *</label>\n"
                + "      <input type=\"text\" id=\"title\" name=\"title\" value=\"Weekly Team Meeting\" required>\n"
                + "    </div>\n"
                + "    <div class=\"form-group\">\n"
                + "      <label for=\"description\">Description</label>\n"
                + "      <textarea id=\"description\" name=\"description\">Discuss project updates and blockers.</textarea>\n"
                + "    </div>\n"
                + "    <div class=\"form-group\">\n"
                + "      <label for=\"location\">Location</label>\n"
                + "      <input type=\"text\" id=\"location\" name=\"location\" placeholder=\"Conference Room A\">\n"
                + "    </div>\n"
                + "    <div class=\"form-group\">\n"
                + "      <label for=\"attendees\">Attendees</label>\n"
                + "      <textarea id=\"attendees\" name=\"attendees\" placeholder=\"email1@example.com&#10;email2@example.com\"></textarea>\n"
                + "      <div class=\"help-text\">One email address per line</div>\n"
                + "    </div>\n"
                + "  </div>\n"

                + "  <div class=\"form-section\">\n"
                + "    <h3 class=\"section-title\">Date &amp; Time</h3>\n"
                + "    <div class=\"checkbox-group\" style=\"margin-bottom:.9rem\">\n"
                + "      <input type=\"checkbox\" id=\"allDay\" name=\"allDay\">\n"
                + "      <label for=\"allDay\">All-day event</label>\n"
                + "    </div>\n"
                + "    <div class=\"form-row\">\n"
                + "      <div class=\"form-group\">\n"
                + "        <label for=\"startDate\">Start *</label>\n"
                + "        <input type=\"datetime-local\" id=\"startDate\" name=\"startDate\" value=\"" + h(defaultStart) + "\" required>\n"
                + "      </div>\n"
                + "      <div class=\"form-group\">\n"
                + "        <label for=\"endDate\">End *</label>\n"
                + "        <input type=\"datetime-local\" id=\"endDate\" name=\"endDate\" value=\"" + h(defaultEnd) + "\" required>\n"
                + "      </div>\n"
                + "    </div>\n"
                + "  </div>\n"

                + "  <div class=\"form-section\">\n"
                + "    <div class=\"recurrence-toggle\" onclick=\"toggleRecurrence()\">\n"
                + "      <input type=\"checkbox\" id=\"recurrenceEnabled\" name=\"recurrenceEnabled\" onclick=\"event.stopPropagation();toggleRecurrence()\">\n"
                + "      <label for=\"recurrenceEnabled\">Add Recurrence</label>\n"
                + "    </div>\n"
                + "    <div id=\"recurrenceContent\" class=\"recurrence-content\">\n"
                + "      <div class=\"form-row\">\n"
                + "        <div class=\"form-group\">\n"
                + "          <label for=\"frequency\">Frequency</label>\n"
                + "          <select id=\"frequency\" name=\"frequency\">\n"
                + "            <option value=\"DAILY\">Daily</option>\n"
                + "            <option value=\"WEEKLY\">Weekly</option>\n"
                + "            <option value=\"MONTHLY\">Monthly</option>\n"
                + "            <option value=\"YEARLY\">Yearly</option>\n"
                + "          </select>\n"
                + "        </div>\n"
                + "        <div class=\"form-group\">\n"
                + "          <label for=\"interval\">Interval</label>\n"
                + "          <input type=\"number\" id=\"interval\" name=\"interval\" value=\"1\" min=\"1\">\n"
                + "          <div class=\"help-text\">Repeat every N days/weeks/months</div>\n"
                + "        </div>\n"
                + "      </div>\n"
                + "      <div class=\"form-group\">\n"
                + "        <label for=\"count\">Count</label>\n"
                + "        <input type=\"number\" id=\"count\" name=\"count\" value=\"3\" min=\"1\">\n"
                + "        <div class=\"help-text\">Number of occurrences</div>\n"
                + "      </div>\n"
                + "      <div class=\"form-group\">\n"
                + "        <label for=\"byDay\">By Day</label>\n"
                + "        <input type=\"text\" id=\"byDay\" name=\"byDay\" value=\"MO,TU,WE,TH,FR,SA,SU\" placeholder=\"MO,TU,WE,TH,FR\">\n"
                + "        <div class=\"help-text\">Comma-separated: SU,MO,TU,WE,TH,FR,SA</div>\n"
                + "      </div>\n"
                + "    </div>\n"
                + "  </div>\n"

                + "  <div class=\"btn-group\">\n"
                + "    <button type=\"submit\" class=\"btn btn-primary\" id=\"submitBtn\">Create / Update Event</button>\n"
                + "    <button type=\"button\" class=\"btn btn-danger\" id=\"deleteBtn\" onclick=\"handleDelete()\">Delete Event</button>\n"
                + "  </div>\n"
                + "</form>\n"
                + "<script>window.CAL_DATA = " + calJson + ";</script>\n"
                + "<script src=\"/event-edit.js\"></script>\n";

        return layout("Event Edit", "event-edit", body, true);
    }

    // ── API: events (JSON, called by event-edit.js) ──────────────────────────

    @PostMapping(value = "/api/events", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> apiCreate(@RequestBody EventApiBody body, HttpServletRequest req, HttpServletResponse res) {
        TokenResponse tokens = readTokens(req);
        if (tokens == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        bind(tokens, res);
        try {
            CalendarEvent evt = client.events().create(body.toCreateData());
            return ResponseEntity.ok(evt);
        } catch (MobiscrollConnectException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping(value = "/api/events", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> apiUpdate(@RequestBody EventApiBody body, HttpServletRequest req, HttpServletResponse res) {
        TokenResponse tokens = readTokens(req);
        if (tokens == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        bind(tokens, res);
        try {
            CalendarEvent evt = client.events().update(body.toUpdateData());
            return ResponseEntity.ok(evt);
        } catch (MobiscrollConnectException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping(value = "/api/events", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> apiDelete(@RequestBody EventApiBody body, HttpServletRequest req, HttpServletResponse res) {
        TokenResponse tokens = readTokens(req);
        if (tokens == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        bind(tokens, res);
        try {
            client.events().delete(body.toDeleteParams());
            return ResponseEntity.ok(Map.of("success", true));
        } catch (MobiscrollConnectException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
        }
    }

    // ── Shared request body for /api/events ─────────────────────────────────

    public static class EventApiBody {
        public String provider;
        public String calendarId;
        public String eventId;
        public String recurringEventId;
        public String title;
        public String description;
        public String location;
        public String start;
        public String end;
        public Boolean allDay;
        public List<String> attendees;
        public String updateMode;
        public String deleteMode;
        public RecurrenceBody recurrence;

        EventCreateData toCreateData() {
            EventCreateData.Builder b = EventCreateData.builder()
                    .provider(providerOf(provider))
                    .calendarId(calendarId)
                    .title(title)
                    .description(description)
                    .location(location)
                    .allDay(allDay)
                    .attendees(attendees);
            if (start != null) b.start(OffsetDateTime.parse(start));
            if (end   != null) b.end(OffsetDateTime.parse(end));
            return b.build();
        }

        EventUpdateData toUpdateData() {
            EventUpdateData.Builder b = EventUpdateData.builder()
                    .provider(providerOf(provider))
                    .calendarId(calendarId)
                    .eventId(eventId)
                    .title(title)
                    .description(description)
                    .location(location)
                    .allDay(allDay)
                    .attendees(attendees);
            if (start != null) b.start(OffsetDateTime.parse(start));
            if (end   != null) b.end(OffsetDateTime.parse(end));
            return b.build();
        }

        EventDeleteParams toDeleteParams() {
            return EventDeleteParams.builder()
                    .provider(providerOf(provider))
                    .calendarId(calendarId)
                    .eventId(eventId)
                    .build();
        }

        private static com.mobiscroll.connect.Provider providerOf(String s) {
            if (s == null) return null;
            switch (s.toLowerCase()) {
                case "google":    return com.mobiscroll.connect.Provider.GOOGLE;
                case "microsoft": return com.mobiscroll.connect.Provider.MICROSOFT;
                case "apple":     return com.mobiscroll.connect.Provider.APPLE;
                case "caldav":    return com.mobiscroll.connect.Provider.CALDAV;
                default:          return null;
            }
        }
    }

    public static class RecurrenceBody {
        public String frequency;
        public Integer interval;
        public Integer count;
        public List<String> byDay;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String calendarDisplayName(Calendar c) {
        if (c.getTitle() != null && !c.getTitle().isEmpty()) return c.getTitle();
        // Fall back to additional fields the provider may return under a different key.
        for (String key : new String[]{"name", "displayName", "title"}) {
            Object v = c.getAdditional().get(key);
            if (v instanceof String && !((String) v).isEmpty()) return (String) v;
        }
        return c.getId() != null ? c.getId() : "";
    }

    private static OffsetDateTime parseOrDefault(String s, OffsetDateTime def) {
        if (s == null || s.isEmpty()) return def;
        try { return OffsetDateTime.parse(s + ":00Z"); } catch (Exception ignored) {}
        try { return OffsetDateTime.parse(s); } catch (Exception ignored) {}
        return def;
    }
}
