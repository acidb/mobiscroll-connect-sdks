using System.Text.Json;
using Microsoft.AspNetCore.Http;
using Mobiscroll.Connect;
using Mobiscroll.Connect.DependencyInjection;
using Mobiscroll.Connect.Exceptions;
using Mobiscroll.Connect.Models;

var builder = WebApplication.CreateBuilder(args);
builder.Services.AddHttpContextAccessor();
builder.Services.AddMobiscrollConnect(o =>
{
    o.ClientId = builder.Configuration["Mobiscroll:ClientId"]
        ?? throw new InvalidOperationException("Set Mobiscroll:ClientId in appsettings.Development.json");
    o.ClientSecret = builder.Configuration["Mobiscroll:ClientSecret"]
        ?? throw new InvalidOperationException("Set Mobiscroll:ClientSecret in appsettings.Development.json");
    o.RedirectUri = builder.Configuration["Mobiscroll:RedirectUri"]
        ?? "http://localhost:5050/callback";
});

var app = builder.Build();
app.UseStaticFiles();

// ── Shared JSON options ─────────────────────────────────────────────────────
var jsonOpts = new JsonSerializerOptions { WriteIndented = true };

// ── Token helpers (httpOnly cookies) ───────────────────────────────────────
static TokenResponse? ReadTokens(HttpContext ctx)
{
    var at = ctx.Request.Cookies["access_token"];
    var rt = ctx.Request.Cookies["refresh_token"];
    if (string.IsNullOrEmpty(at) && string.IsNullOrEmpty(rt)) return null;
    return new TokenResponse { AccessToken = at ?? string.Empty, RefreshToken = rt, TokenType = "Bearer" };
}

static void WriteTokens(HttpContext ctx, TokenResponse tokens)
{
    if (!string.IsNullOrEmpty(tokens.AccessToken))
        ctx.Response.Cookies.Append("access_token", tokens.AccessToken,
            new CookieOptions { HttpOnly = true, SameSite = SameSiteMode.Lax, Path = "/", MaxAge = TimeSpan.FromDays(7) });
    if (!string.IsNullOrEmpty(tokens.RefreshToken))
        ctx.Response.Cookies.Append("refresh_token", tokens.RefreshToken,
            new CookieOptions { HttpOnly = true, SameSite = SameSiteMode.Lax, Path = "/", MaxAge = TimeSpan.FromDays(30) });
}

static void ClearTokens(HttpContext ctx)
{
    ctx.Response.Cookies.Delete("access_token");
    ctx.Response.Cookies.Delete("refresh_token");
}

static MobiscrollConnectClient Bind(MobiscrollConnectClient client, TokenResponse tokens, HttpContext ctx)
{
    client.SetCredentials(tokens);
    client.OnTokensRefreshed(t => WriteTokens(ctx, t));
    return client;
}

// ── Shared HTML shell ───────────────────────────────────────────────────────
static string H(string? s) => System.Net.WebUtility.HtmlEncode(s ?? string.Empty);

static string Layout(string title, string activePage, string body, bool loggedIn = false) => $"""
    <!doctype html>
    <html lang="en">
    <head>
      <meta charset="utf-8">
      <meta name="viewport" content="width=device-width,initial-scale=1">
      <title>{H(title)} — Mobiscroll Connect .NET</title>
      <link rel="stylesheet" href="/app.css">
    </head>
    <body>
      <header class="nav-header">
        <h2>Mobiscroll Connect — .NET SDK demo</h2>
        <nav class="nav-menu">
          <a href="/" class="{(activePage == "home" ? "active" : "")}">Home</a>
          <a href="/calendars" class="{(activePage == "calendars" ? "active" : "")}">Calendars</a>
          <a href="/events" class="{(activePage == "events" ? "active" : "")}">Events</a>
          <a href="/event-edit" class="{(activePage == "event-edit" ? "active" : "")}">Event Edit</a>
          {(loggedIn ? """<a href="/logout" style="background:#dc3545">Logout</a>""" : "")}
        </nav>
      </header>
      <main class="page">
        {body}
      </main>
    </body>
    </html>
    """;

// ── HOME ────────────────────────────────────────────────────────────────────
app.MapGet("/", (MobiscrollConnectClient client, HttpContext ctx) =>
{
    var tokens = ReadTokens(ctx);
    var authUrl = client.Auth.GenerateAuthUrl(new AuthorizeParams
    {
        UserId = "demo-user",
        Providers = "google,microsoft,apple,caldav",
    });

    var body = tokens is null
        ? $"""
          <div class="alert alert-info">
            <strong>Not connected.</strong> Connect a calendar account to get started.
          </div>
          <a href="{H(authUrl)}" class="btn btn-primary">Connect calendar account</a>
          """
        : """
          <div class="alert alert-success">
            <strong>Connected!</strong> You have an active session.
          </div>
          <p>
            <a href="/calendars" class="btn btn-primary" style="margin-right:.5rem">View Calendars</a>
            <a href="/events"    class="btn btn-primary" style="margin-right:.5rem">View Events</a>
            <a href="/event-edit" class="btn btn-primary">Create / Edit Event</a>
          </p>
          """;

    return Results.Content(Layout("Home", "home", body, tokens is not null), "text/html");
});

// ── CALLBACK ────────────────────────────────────────────────────────────────
app.MapGet("/callback", async (string? code, string? error, MobiscrollConnectClient client, HttpContext ctx) =>
{
    if (!string.IsNullOrEmpty(error))
        return Results.Content(
            Layout("Error", "", $"<div class='alert alert-error'>OAuth error: {H(error)}</div>"), "text/html");
    if (string.IsNullOrEmpty(code))
        return Results.BadRequest("Missing ?code");

    var tokens = await client.Auth.GetTokenAsync(code);
    WriteTokens(ctx, tokens);
    return Results.Redirect("/calendars");
});

// ── LOGOUT ──────────────────────────────────────────────────────────────────
app.MapGet("/logout", (HttpContext ctx) =>
{
    ClearTokens(ctx);
    return Results.Redirect("/");
});

// ── CALENDARS PAGE ──────────────────────────────────────────────────────────
app.MapGet("/calendars", async (MobiscrollConnectClient client, HttpContext ctx) =>
{
    var tokens = ReadTokens(ctx);
    if (tokens is null) return Results.Redirect("/");
    Bind(client, tokens, ctx);

    IReadOnlyList<Calendar>? calendars = null;
    string? errorMsg = null;
    try { calendars = await client.Calendars.ListAsync(); }
    catch (Exception ex) { errorMsg = ex.Message; }

    var rows = calendars?.Count > 0
        ? string.Join("", calendars.Select(c => $"""
            <tr>
              <td><span class="provider-badge provider-{c.Provider.ToWireString()}">{c.Provider.ToWireString()}</span></td>
              <td class="event-title">{H(c.Title)}</td>
              <td><code style="font-size:.75rem">{H(c.Id)}</code></td>
              <td>{H(c.TimeZone)}</td>
              <td><a href="/event-edit?calendarId={Uri.EscapeDataString(c.Id)}&provider={c.Provider.ToWireString()}">create event</a></td>
            </tr>
            """))
        : "<tr><td colspan='5' style='text-align:center;color:#6b7280;padding:2rem'>No calendars found.</td></tr>";

    var body = $"""
        <h1>Calendars</h1>
        {(errorMsg is not null ? $"<div class='alert alert-error'>{H(errorMsg)}</div>" : "")}
        <div class="events-table-wrap">
          <table>
            <thead><tr><th>Provider</th><th>Title</th><th>ID</th><th>Timezone</th><th></th></tr></thead>
            <tbody>{rows}</tbody>
          </table>
        </div>
        """;

    return Results.Content(Layout("Calendars", "calendars", body, true), "text/html");
});

// ── EVENTS PAGE ─────────────────────────────────────────────────────────────
app.MapGet("/events", async (
    HttpContext ctx,
    MobiscrollConnectClient client,
    string? start, string? end, int? pageSize, bool? singleEvents, string? nextPageToken) =>
{
    var tokens = ReadTokens(ctx);
    if (tokens is null) return Results.Redirect("/");
    Bind(client, tokens, ctx);

    var now = DateTime.UtcNow;
    var startDt = string.IsNullOrEmpty(start)
        ? now.AddDays(-31)
        : DateTime.TryParse(start, out var sd) ? sd.ToUniversalTime() : now.AddDays(-31);
    var endDt = string.IsNullOrEmpty(end)
        ? now.AddDays(90)
        : DateTime.TryParse(end, out var ed) ? ed.ToUniversalTime() : now.AddDays(90);
    var ps = Math.Clamp(pageSize ?? 100, 1, 1000);
    var single = singleEvents ?? true;

    EventsListResponse? result = null;
    string? errorMsg = null;
    try
    {
        result = await client.Events.ListAsync(new EventListParams
        {
            Start = startDt,
            End = endDt,
            PageSize = ps,
            SingleEvents = single,
            NextPageToken = nextPageToken,
        });
    }
    catch (Exception ex) { errorMsg = ex.Message; }

    var startVal = startDt.ToString("yyyy-MM-ddTHH:mm");
    var endVal = endDt.ToString("yyyy-MM-ddTHH:mm");

    var rows = result?.Events.Count > 0
        ? string.Join("", result.Events.Select(e => $"""
            <tr>
              <td><span class="provider-badge provider-{e.Provider.ToWireString()}">{e.Provider.ToWireString()}</span></td>
              <td>
                <div class="event-title">{H(e.Title)}</div>
                <div class="event-meta">{H(e.CalendarId)}</div>
              </td>
              <td>{e.Start:yyyy-MM-dd HH:mm}<br><span class="event-meta">→ {e.End:HH:mm}</span></td>
              <td>{(e.AllDay ? "<span style='color:#6b7280;font-size:.8rem'>all day</span>" : "")}</td>
              <td><a href="/event-edit?eventId={Uri.EscapeDataString(e.Id)}&calendarId={Uri.EscapeDataString(e.CalendarId)}&provider={e.Provider.ToWireString()}">edit</a></td>
            </tr>
            """))
        : "<tr><td colspan='5' style='text-align:center;color:#6b7280;padding:2rem'>No events found for this range.</td></tr>";

    var nextPageQs = string.IsNullOrEmpty(result?.NextPageToken) ? "" :
        $"start={Uri.EscapeDataString(startVal)}&end={Uri.EscapeDataString(endVal)}&pageSize={ps}&singleEvents={single}&nextPageToken={Uri.EscapeDataString(result.NextPageToken)}";

    var eventsJson = result is not null
        ? H(JsonSerializer.Serialize(result, jsonOpts))
        : "";

    var body = $"""
        <h1>Events</h1>

        <div class="filter-form">
          <form method="get" action="/events">
            <div class="filter-row">
              <div class="filter-group">
                <label for="start">Start date</label>
                <input type="datetime-local" id="start" name="start" value="{startVal}">
              </div>
              <div class="filter-group">
                <label for="end">End date</label>
                <input type="datetime-local" id="end" name="end" value="{endVal}">
              </div>
              <div class="filter-group">
                <label for="pageSize">Page size</label>
                <input type="number" id="pageSize" name="pageSize" min="1" max="1000" value="{ps}">
              </div>
              <button type="submit" class="btn btn-primary">Load</button>
            </div>
            <div class="checkbox-group">
              <input type="checkbox" id="singleEvents" name="singleEvents" value="true" {(single ? "checked" : "")}>
              <label for="singleEvents">Single Events (expand recurring)</label>
            </div>
          </form>
        </div>

        <div class="api-info">
          <span class="api-badge">GET</span>
          <span class="api-url">/events?start={H(startVal)}&amp;end={H(endVal)}&amp;pageSize={ps}&amp;singleEvents={single}{(nextPageToken is not null ? "&amp;nextPageToken=…" : "")}</span>
        </div>

        {(errorMsg is not null ? $"<div class='alert alert-error'>{H(errorMsg)}</div>" : "")}

        <div class="events-table-wrap">
          <table>
            <thead><tr><th>Provider</th><th>Title / Calendar</th><th>Start → End</th><th></th><th></th></tr></thead>
            <tbody>{rows}</tbody>
          </table>
        </div>

        {(result?.Events.Count > 0 ? $"""
          <details style="margin-top:1.5rem">
            <summary style="cursor:pointer;font-weight:600;color:#374151">Raw API response JSON</summary>
            <pre class="result-box">{eventsJson}</pre>
          </details>
          """ : "")}

        {(!string.IsNullOrEmpty(result?.NextPageToken) ? $"""
          <div class="pagination-bar">
            <a href="/events?{H(nextPageQs)}" class="btn btn-primary">Load next page →</a>
          </div>
          """ : "")}
        """;

    return Results.Content(Layout("Events", "events", body, true), "text/html");
});

// ── EVENT EDIT PAGE ─────────────────────────────────────────────────────────
app.MapGet("/event-edit", async (
    HttpContext ctx,
    MobiscrollConnectClient client,
    string? eventId, string? calendarId, string? provider) =>
{
    var tokens = ReadTokens(ctx);
    if (tokens is null) return Results.Redirect("/");
    Bind(client, tokens, ctx);

    IReadOnlyList<Calendar> calendars = new List<Calendar>();
    try { calendars = await client.Calendars.ListAsync(); }
    catch { /* show empty dropdown */ }

    var now = DateTime.UtcNow;
    var defaultStart = now.ToString("yyyy-MM-ddTHH:mm");
    var defaultEnd = now.AddHours(1).ToString("yyyy-MM-ddTHH:mm");

    // Calendar options for the initial server-rendered dropdown (filtered by provider if supplied)
    var calOptions = string.Join("", calendars
        .Where(c => string.IsNullOrEmpty(provider) || c.Provider.ToWireString() == provider)
        .Select(c => $"""<option value="{H(c.Id)}" {(c.Id == calendarId ? "selected" : "")}>{H(c.Title)} ({c.Provider.ToWireString()})</option>"""));

    // Full calendar list as JSON for the provider-change JS dropdown
    var calJson = JsonSerializer.Serialize(
        calendars.Select(c => new { id = c.Id, title = c.Title, provider = c.Provider.ToWireString() }));

    var body = $"""
        <h1>Create / Edit Event</h1>

        <div id="result-box" class="alert" style="display:none"></div>

        <form class="event-form" id="event-form">

          <div class="form-section">
            <h3 class="section-title">Event Settings</h3>
            <div class="form-row">
              <div class="form-group">
                <label for="provider">Provider *</label>
                <select id="provider" name="provider" required>
                  <option value="">Select provider…</option>
                  <option value="google"    {(provider == "google" ? "selected" : "")}>Google</option>
                  <option value="microsoft" {(provider == "microsoft" ? "selected" : "")}>Microsoft</option>
                  <option value="apple"     {(provider == "apple" ? "selected" : "")}>Apple</option>
                  <option value="caldav"    {(provider == "caldav" ? "selected" : "")}>CalDAV</option>
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
              <input type="text" id="eventId" name="eventId" value="{H(eventId ?? "")}" placeholder="Leave blank to create a new event">
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
                {calOptions}
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
                <input type="datetime-local" id="startDate" name="startDate" value="{defaultStart}" required>
              </div>
              <div class="form-group">
                <label for="endDate">End *</label>
                <input type="datetime-local" id="endDate" name="endDate" value="{defaultEnd}" required>
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

        <script>window.CAL_DATA = {calJson};</script>
        <script src="/event-edit.js"></script>
        """;

    return Results.Content(Layout("Event Edit", "event-edit", body, true), "text/html");
});

// ── API: events (JSON, called by JS on event-edit page) ─────────────────────
app.MapPost("/api/events", async (HttpContext ctx, MobiscrollConnectClient client) =>
{
    var tokens = ReadTokens(ctx);
    if (tokens is null) return Results.Json(new { error = "Not authenticated" }, statusCode: 401);
    Bind(client, tokens, ctx);

    EventCreateData? body;
    try { body = await ctx.Request.ReadFromJsonAsync<EventCreateData>(); }
    catch { return Results.Json(new { error = "Invalid JSON body" }, statusCode: 400); }
    if (body is null) return Results.Json(new { error = "Missing body" }, statusCode: 400);

    try
    {
        var evt = await client.Events.CreateAsync(body);
        return Results.Json(evt);
    }
    catch (MobiscrollConnectException ex) { return Results.Json(new { error = ex.Message }, statusCode: 400); }
    catch (Exception ex) { return Results.Json(new { error = ex.Message }, statusCode: 500); }
});

app.MapMethods("/api/events", new[] { "PUT" }, async (HttpContext ctx, MobiscrollConnectClient client) =>
{
    var tokens = ReadTokens(ctx);
    if (tokens is null) return Results.Json(new { error = "Not authenticated" }, statusCode: 401);
    Bind(client, tokens, ctx);

    EventUpdateData? body;
    try { body = await ctx.Request.ReadFromJsonAsync<EventUpdateData>(); }
    catch { return Results.Json(new { error = "Invalid JSON body" }, statusCode: 400); }
    if (body is null) return Results.Json(new { error = "Missing body" }, statusCode: 400);

    try
    {
        var evt = await client.Events.UpdateAsync(body);
        return Results.Json(evt);
    }
    catch (MobiscrollConnectException ex) { return Results.Json(new { error = ex.Message }, statusCode: 400); }
    catch (Exception ex) { return Results.Json(new { error = ex.Message }, statusCode: 500); }
});

app.MapDelete("/api/events", async (HttpContext ctx, MobiscrollConnectClient client) =>
{
    var tokens = ReadTokens(ctx);
    if (tokens is null) return Results.Json(new { error = "Not authenticated" }, statusCode: 401);
    Bind(client, tokens, ctx);

    EventDeleteParams? body;
    try { body = await ctx.Request.ReadFromJsonAsync<EventDeleteParams>(); }
    catch { return Results.Json(new { error = "Invalid JSON body" }, statusCode: 400); }
    if (body is null) return Results.Json(new { error = "Missing body" }, statusCode: 400);

    try
    {
        await client.Events.DeleteAsync(body);
        return Results.Json(new { success = true });
    }
    catch (MobiscrollConnectException ex) { return Results.Json(new { error = ex.Message }, statusCode: 400); }
    catch (Exception ex) { return Results.Json(new { error = ex.Message }, statusCode: 500); }
});

app.Run();
