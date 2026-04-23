using System.Text.Json;
using Mobiscroll.Connect.Models;

namespace Mobiscroll.Connect.Tests;

public class SerializationTests
{
    [Theory]
    [InlineData(Provider.Google, "google")]
    [InlineData(Provider.Microsoft, "microsoft")]
    [InlineData(Provider.Apple, "apple")]
    [InlineData(Provider.CalDav, "caldav")]
    public void Provider_SerializesToLowercaseWireString(Provider provider, string wire)
    {
        Assert.Equal(wire, provider.ToWireString());
        var json = JsonSerializer.Serialize(provider, ApiClient.JsonOptions);
        Assert.Equal($"\"{wire}\"", json);
    }

    [Theory]
    [InlineData("google", Provider.Google)]
    [InlineData("microsoft", Provider.Microsoft)]
    [InlineData("apple", Provider.Apple)]
    [InlineData("caldav", Provider.CalDav)]
    public void Provider_DeserializesFromLowercaseWireString(string wire, Provider expected)
    {
        var actual = JsonSerializer.Deserialize<Provider>($"\"{wire}\"", ApiClient.JsonOptions);
        Assert.Equal(expected, actual);
    }

    [Fact]
    public void TokenResponse_RoundTripsSnakeCaseFields()
    {
        var json = """{"access_token":"at","token_type":"Bearer","expires_in":3600,"refresh_token":"rt"}""";
        var t = JsonSerializer.Deserialize<TokenResponse>(json, ApiClient.JsonOptions);
        Assert.NotNull(t);
        Assert.Equal("at", t!.AccessToken);
        Assert.Equal("Bearer", t.TokenType);
        Assert.Equal(3600, t.ExpiresIn);
        Assert.Equal("rt", t.RefreshToken);

        var round = JsonSerializer.Serialize(t, ApiClient.JsonOptions);
        Assert.Contains("\"access_token\":", round);
        Assert.Contains("\"refresh_token\":", round);
    }

    [Fact]
    public void EventCreateData_SerializesCamelCaseAndOmitsNullFields()
    {
        var data = new EventCreateData
        {
            Provider = Provider.Google,
            CalendarId = "primary",
            Title = "T",
            Start = new System.DateTime(2026, 1, 1, 0, 0, 0, System.DateTimeKind.Utc),
            End = new System.DateTime(2026, 1, 1, 1, 0, 0, System.DateTimeKind.Utc),
        };

        var json = JsonSerializer.Serialize(data, ApiClient.JsonOptions);
        Assert.Contains("\"calendarId\":", json);
        Assert.Contains("\"provider\":\"google\"", json);
        Assert.DoesNotContain("\"description\":", json);
        Assert.DoesNotContain("\"location\":", json);
    }
}
