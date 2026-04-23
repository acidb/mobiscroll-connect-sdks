using System.Net;
using System.Threading.Tasks;
using Mobiscroll.Connect.Models;
using Mobiscroll.Connect.Tests.TestHelpers;

namespace Mobiscroll.Connect.Tests;

public class CalendarsTests
{
    [Fact]
    public async Task ListAsync_ParsesProviderEnumAndFields()
    {
        var json = """
        [
          {"provider":"google","id":"cal-1","title":"Work","timeZone":"UTC","color":"#fff","description":""},
          {"provider":"microsoft","id":"cal-2","title":"Personal","timeZone":"Europe/Budapest","color":"#000","description":"pers"},
          {"provider":"caldav","id":"cal-3","title":"Shared","timeZone":"UTC","color":"","description":""}
        ]
        """;

        var handler = new FakeHttpMessageHandler().Enqueue(HttpStatusCode.OK, json);
        using var client = ClientFactory.Create(handler);
        client.SetCredentials(new TokenResponse { AccessToken = "at" });

        var calendars = await client.Calendars.ListAsync();

        Assert.Equal(3, calendars.Count);
        Assert.Equal(Provider.Google, calendars[0].Provider);
        Assert.Equal(Provider.Microsoft, calendars[1].Provider);
        Assert.Equal(Provider.CalDav, calendars[2].Provider);
        Assert.Equal("Work", calendars[0].Title);
        Assert.Equal("Europe/Budapest", calendars[1].TimeZone);

        var req = handler.Requests[0];
        Assert.Equal(HttpMethod.Get, req.Method);
        Assert.EndsWith("/calendars", req.Uri.AbsolutePath);
        Assert.Equal("Bearer at", req.Headers["Authorization"]);
    }

    [Fact]
    public async Task ListAsync_EmptyBodyReturnsEmptyList()
    {
        var handler = new FakeHttpMessageHandler().Enqueue(HttpStatusCode.OK, "[]");
        using var client = ClientFactory.Create(handler);
        client.SetCredentials(new TokenResponse { AccessToken = "at" });

        var calendars = await client.Calendars.ListAsync();
        Assert.Empty(calendars);
    }
}
