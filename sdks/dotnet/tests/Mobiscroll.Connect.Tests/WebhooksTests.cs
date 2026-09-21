using System.Net;
using System.Text.Json;
using System.Threading.Tasks;
using Mobiscroll.Connect.Exceptions;
using Mobiscroll.Connect.Models;
using Mobiscroll.Connect.Tests.TestHelpers;

namespace Mobiscroll.Connect.Tests;

public class WebhooksTests
{
    [Fact]
    public async Task SubscribeWebhookAsync_PostsCamelCaseJsonAndParsesResponse()
    {
        var responseJson = """
        {
          "success": true,
          "provider": "google",
          "subscription": {"channelId": "chan-1", "resourceId": "res-1", "expiration": "2026-10-01T00:00:00.000Z"},
          "serverWebhookUrl": "https://connect.mobiscroll.com/api/webhook-callback",
          "channelId": "chan-1"
        }
        """;
        var handler = new FakeHttpMessageHandler().Enqueue(HttpStatusCode.OK, responseJson);
        using var client = ClientFactory.Create(handler);
        client.SetCredentials(new TokenResponse { AccessToken = "at" });

        var result = await client.Webhooks.SubscribeWebhookAsync(new WebhookSubscribeData
        {
            Provider = Provider.Google,
            CalendarId = "primary",
            ChannelId = "chan-1",
            Expiration = 1735689600000,
        });

        Assert.True(result.Success);
        Assert.Equal("google", result.Provider);
        Assert.Equal("chan-1", result.Subscription.ChannelId);
        Assert.Equal("res-1", result.Subscription.ResourceId);
        Assert.Equal("2026-10-01T00:00:00.000Z", result.Subscription.Expiration);
        Assert.Equal("https://connect.mobiscroll.com/api/webhook-callback", result.ServerWebhookUrl);
        Assert.Equal("chan-1", result.ChannelId);

        var req = handler.Requests[0];
        Assert.Equal(HttpMethod.Post, req.Method);
        Assert.EndsWith("/subscribe-webhook", req.Uri.AbsolutePath);
        Assert.Equal("Bearer at", req.Headers["Authorization"]);

        var body = JsonDocument.Parse(req.Body!);
        Assert.Equal("google", body.RootElement.GetProperty("provider").GetString());
        Assert.Equal("primary", body.RootElement.GetProperty("calendarId").GetString());
        Assert.Equal("chan-1", body.RootElement.GetProperty("channelId").GetString());
        Assert.Equal(1735689600000, body.RootElement.GetProperty("expiration").GetInt64());
    }

    [Fact]
    public async Task SubscribeWebhookAsync_OmitsOptionalFieldsWhenNull()
    {
        var responseJson = """
        {
          "success": true,
          "provider": "microsoft",
          "subscription": {"channelId": "auto-chan"},
          "serverWebhookUrl": "https://connect.mobiscroll.com/api/webhook-callback",
          "channelId": "auto-chan"
        }
        """;
        var handler = new FakeHttpMessageHandler().Enqueue(HttpStatusCode.OK, responseJson);
        using var client = ClientFactory.Create(handler);
        client.SetCredentials(new TokenResponse { AccessToken = "at" });

        var result = await client.Webhooks.SubscribeWebhookAsync(new WebhookSubscribeData
        {
            Provider = Provider.Microsoft,
            CalendarId = "primary",
        });

        Assert.Equal("auto-chan", result.Subscription.ChannelId);
        Assert.Null(result.Subscription.ResourceId);
        Assert.Null(result.Subscription.Expiration);

        var body = JsonDocument.Parse(handler.Requests[0].Body!);
        Assert.False(body.RootElement.TryGetProperty("channelId", out _));
        Assert.False(body.RootElement.TryGetProperty("expiration", out _));
    }

    [Theory]
    [InlineData(HttpStatusCode.BadRequest, typeof(ValidationException))]
    [InlineData(HttpStatusCode.Unauthorized, typeof(AuthenticationException))]
    [InlineData(HttpStatusCode.InternalServerError, typeof(ServerException))]
    public async Task SubscribeWebhookAsync_MapsErrorStatusCodes(HttpStatusCode status, System.Type expected)
    {
        var handler = new FakeHttpMessageHandler().Enqueue(status, """{"message":"boom"}""");
        using var client = ClientFactory.Create(handler);
        client.SetCredentials(new TokenResponse { AccessToken = "at" });

        var ex = await Assert.ThrowsAsync(expected, () => client.Webhooks.SubscribeWebhookAsync(new WebhookSubscribeData
        {
            Provider = Provider.Google,
            CalendarId = "primary",
        }));
        Assert.Contains("boom", ex.Message);
    }

    [Fact]
    public async Task UnsubscribeWebhookAsync_PostsCamelCaseJsonAndParsesResponse()
    {
        var handler = new FakeHttpMessageHandler()
            .Enqueue(HttpStatusCode.OK, """{"success":true}""");
        using var client = ClientFactory.Create(handler);
        client.SetCredentials(new TokenResponse { AccessToken = "at" });

        var result = await client.Webhooks.UnsubscribeWebhookAsync(new WebhookUnsubscribeData
        {
            Provider = Provider.Google,
            ChannelId = "chan-1",
            ResourceId = "res-1",
        });

        Assert.True(result.Success);
        Assert.Null(result.Message);

        var req = handler.Requests[0];
        Assert.Equal(HttpMethod.Post, req.Method);
        Assert.EndsWith("/unsubscribe-webhook", req.Uri.AbsolutePath);

        var body = JsonDocument.Parse(req.Body!);
        Assert.Equal("google", body.RootElement.GetProperty("provider").GetString());
        Assert.Equal("chan-1", body.RootElement.GetProperty("channelId").GetString());
        Assert.Equal("res-1", body.RootElement.GetProperty("resourceId").GetString());
    }

    [Fact]
    public async Task UnsubscribeWebhookAsync_ReturnsSuccessTrueWithMessageOnAlreadyExpiredSubscription()
    {
        var handler = new FakeHttpMessageHandler()
            .Enqueue(HttpStatusCode.OK, """{"success":true,"message":"Subscription had already expired"}""");
        using var client = ClientFactory.Create(handler);
        client.SetCredentials(new TokenResponse { AccessToken = "at" });

        var result = await client.Webhooks.UnsubscribeWebhookAsync(new WebhookUnsubscribeData
        {
            Provider = Provider.Google,
            ChannelId = "chan-1",
        });

        Assert.True(result.Success);
        Assert.Equal("Subscription had already expired", result.Message);
    }

    [Theory]
    [InlineData(HttpStatusCode.BadRequest, typeof(ValidationException))]
    [InlineData(HttpStatusCode.Unauthorized, typeof(AuthenticationException))]
    [InlineData(HttpStatusCode.InternalServerError, typeof(ServerException))]
    public async Task UnsubscribeWebhookAsync_MapsErrorStatusCodes(HttpStatusCode status, System.Type expected)
    {
        var handler = new FakeHttpMessageHandler().Enqueue(status, """{"message":"boom"}""");
        using var client = ClientFactory.Create(handler);
        client.SetCredentials(new TokenResponse { AccessToken = "at" });

        var ex = await Assert.ThrowsAsync(expected, () => client.Webhooks.UnsubscribeWebhookAsync(new WebhookUnsubscribeData
        {
            Provider = Provider.Google,
            ChannelId = "chan-1",
        }));
        Assert.Contains("boom", ex.Message);
    }
}
