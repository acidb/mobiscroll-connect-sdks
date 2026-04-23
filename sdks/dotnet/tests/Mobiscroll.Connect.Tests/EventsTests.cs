using System;
using System.Collections.Generic;
using System.Net;
using System.Text.Json;
using System.Threading.Tasks;
using System.Web;
using Mobiscroll.Connect.Models;
using Mobiscroll.Connect.Tests.TestHelpers;

namespace Mobiscroll.Connect.Tests;

public class EventsTests
{
    [Fact]
    public async Task ListAsync_NoParams_HitsEventsWithoutQueryString()
    {
        var handler = new FakeHttpMessageHandler().Enqueue(HttpStatusCode.OK, """{"events":[]}""");
        using var client = ClientFactory.Create(handler);
        client.SetCredentials(new TokenResponse { AccessToken = "at" });

        var result = await client.Events.ListAsync();

        Assert.Empty(result.Events);
        Assert.Equal("", handler.Requests[0].Uri.Query);
        Assert.EndsWith("/events", handler.Requests[0].Uri.AbsolutePath);
    }

    [Fact]
    public async Task ListAsync_SerializesAllFilterParams()
    {
        var handler = new FakeHttpMessageHandler().Enqueue(HttpStatusCode.OK, """{"events":[]}""");
        using var client = ClientFactory.Create(handler);
        client.SetCredentials(new TokenResponse { AccessToken = "at" });

        await client.Events.ListAsync(new EventListParams
        {
            PageSize = 50,
            Start = new DateTime(2026, 4, 1, 12, 0, 0, DateTimeKind.Utc),
            End = new DateTime(2026, 4, 30, 12, 0, 0, DateTimeKind.Utc),
            NextPageToken = "tok-1",
            SingleEvents = true,
            CalendarIds = new Dictionary<string, List<string>>
            {
                ["google"] = new() { "primary", "work" },
                ["microsoft"] = new() { "inbox" },
            },
        });

        var query = HttpUtility.ParseQueryString(handler.Requests[0].Uri.Query);
        Assert.Equal("50", query["pageSize"]);
        Assert.Equal("2026-04-01T12:00:00.000Z", query["start"]);
        Assert.Equal("2026-04-30T12:00:00.000Z", query["end"]);
        Assert.Equal("tok-1", query["nextPageToken"]);
        Assert.Equal("true", query["singleEvents"]);

        var calendarIds = JsonSerializer.Deserialize<Dictionary<string, List<string>>>(query["calendarIds"]!);
        Assert.NotNull(calendarIds);
        Assert.Equal(new[] { "primary", "work" }, calendarIds!["google"]);
        Assert.Equal(new[] { "inbox" }, calendarIds["microsoft"]);
    }

    [Fact]
    public async Task CreateAsync_PostsCamelCaseJsonAndParsesResponse()
    {
        var responseJson = """
        {
          "provider":"google","id":"evt-1","calendarId":"primary","title":"Created",
          "start":"2026-05-01T10:00:00Z","end":"2026-05-01T11:00:00Z","allDay":false
        }
        """;
        var handler = new FakeHttpMessageHandler().Enqueue(HttpStatusCode.OK, responseJson);
        using var client = ClientFactory.Create(handler);
        client.SetCredentials(new TokenResponse { AccessToken = "at" });

        var created = await client.Events.CreateAsync(new EventCreateData
        {
            Provider = Provider.Google,
            CalendarId = "primary",
            Title = "Meeting",
            Start = new DateTime(2026, 5, 1, 10, 0, 0, DateTimeKind.Utc),
            End = new DateTime(2026, 5, 1, 11, 0, 0, DateTimeKind.Utc),
            AllDay = false,
            Attendees = new() { "a@example.com" },
        });

        Assert.Equal(Provider.Google, created.Provider);
        Assert.Equal("evt-1", created.Id);

        var body = JsonDocument.Parse(handler.Requests[0].Body!);
        Assert.Equal("google", body.RootElement.GetProperty("provider").GetString());
        Assert.Equal("primary", body.RootElement.GetProperty("calendarId").GetString());
        Assert.Equal("Meeting", body.RootElement.GetProperty("title").GetString());
        Assert.Equal(JsonValueKind.False, body.RootElement.GetProperty("allDay").ValueKind);
        Assert.Equal("a@example.com", body.RootElement.GetProperty("attendees")[0].GetString());
    }

    [Fact]
    public async Task UpdateAsync_UsesPutAndIncludesEventId()
    {
        var handler = new FakeHttpMessageHandler().Enqueue(HttpStatusCode.OK,
            """{"provider":"google","id":"evt-1","calendarId":"primary","title":"Updated","start":"2026-05-01T10:00:00Z","end":"2026-05-01T11:00:00Z","allDay":false}""");
        using var client = ClientFactory.Create(handler);
        client.SetCredentials(new TokenResponse { AccessToken = "at" });

        await client.Events.UpdateAsync(new EventUpdateData
        {
            Provider = Provider.Google,
            CalendarId = "primary",
            EventId = "evt-1",
            Title = "Updated",
            Start = new DateTime(2026, 5, 1, 10, 0, 0, DateTimeKind.Utc),
            End = new DateTime(2026, 5, 1, 11, 0, 0, DateTimeKind.Utc),
            UpdateMode = "all",
        });

        var req = handler.Requests[0];
        Assert.Equal(HttpMethod.Put, req.Method);
        var body = JsonDocument.Parse(req.Body!);
        Assert.Equal("evt-1", body.RootElement.GetProperty("eventId").GetString());
        Assert.Equal("all", body.RootElement.GetProperty("updateMode").GetString());
    }

    [Fact]
    public async Task DeleteAsync_SendsDeleteWithQueryParams()
    {
        var handler = new FakeHttpMessageHandler().Enqueue(HttpStatusCode.NoContent);
        using var client = ClientFactory.Create(handler);
        client.SetCredentials(new TokenResponse { AccessToken = "at" });

        await client.Events.DeleteAsync(new EventDeleteParams
        {
            Provider = Provider.Microsoft,
            CalendarId = "cal-1",
            EventId = "evt-1",
            DeleteMode = "following",
        });

        var req = handler.Requests[0];
        Assert.Equal(HttpMethod.Delete, req.Method);
        var query = HttpUtility.ParseQueryString(req.Uri.Query);
        Assert.Equal("microsoft", query["provider"]);
        Assert.Equal("cal-1", query["calendarId"]);
        Assert.Equal("evt-1", query["eventId"]);
        Assert.Equal("following", query["deleteMode"]);
    }
}
