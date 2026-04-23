using System.Net;
using System.Text.Json;
using System.Threading.Tasks;
using Mobiscroll.Connect.Exceptions;
using Mobiscroll.Connect.Models;
using Mobiscroll.Connect.Tests.TestHelpers;

namespace Mobiscroll.Connect.Tests;

public class AuthTests
{
    [Fact]
    public void GenerateAuthUrl_BuildsExpectedQueryString()
    {
        using var client = ClientFactory.Create(new FakeHttpMessageHandler());

        var url = client.Auth.GenerateAuthUrl(new AuthorizeParams
        {
            UserId = "user-123",
            State = "xyz",
            Scope = "read-write",
            Providers = "google,microsoft",
        });

        Assert.StartsWith("https://connect.mobiscroll.com/api/oauth/authorize?", url);
        Assert.Contains("response_type=code", url);
        Assert.Contains("client_id=test-client-id", url);
        Assert.Contains("user_id=user-123", url);
        Assert.Contains("redirect_uri=http", url);
        Assert.Contains("state=xyz", url);
        Assert.Contains("scope=read-write", url);
        Assert.Contains("providers=google%2Cmicrosoft", url);
    }

    [Fact]
    public void GenerateAuthUrl_OmitsOptionalParamsWhenNull()
    {
        using var client = ClientFactory.Create(new FakeHttpMessageHandler());

        var url = client.Auth.GenerateAuthUrl(new AuthorizeParams { UserId = "u1" });

        Assert.DoesNotContain("state=", url);
        Assert.DoesNotContain("scope=", url);
        Assert.DoesNotContain("providers=", url);
    }

    [Fact]
    public async Task GetTokenAsync_SendsFormBodyWithBasicAuth_AndSetsCredentials()
    {
        var tokenJson = """{"access_token":"at-1","token_type":"Bearer","expires_in":3600,"refresh_token":"rt-1"}""";
        var handler = new FakeHttpMessageHandler().Enqueue(HttpStatusCode.OK, tokenJson);

        using var client = ClientFactory.Create(handler);
        var tokens = await client.Auth.GetTokenAsync("auth-code-xyz");

        Assert.Equal("at-1", tokens.AccessToken);
        Assert.Equal("rt-1", tokens.RefreshToken);

        var req = handler.Requests[0];
        Assert.Equal(HttpMethod.Post, req.Method);
        Assert.EndsWith("/oauth/token", req.Uri.AbsolutePath);
        Assert.StartsWith("Basic ", req.Headers["Authorization"]);
        Assert.Equal("test-client-id", req.Headers["CLIENT_ID"]);
        Assert.Contains("grant_type=authorization_code", req.Body);
        Assert.Contains("code=auth-code-xyz", req.Body);

        // Credentials were set on the client (follow-up call will carry the bearer).
        Assert.Equal("at-1", client.ApiClient.Credentials?.AccessToken);
    }

    [Fact]
    public async Task GetTokenAsync_ThrowsGenericExchangeErrorOnFailure()
    {
        var handler = new FakeHttpMessageHandler()
            .Enqueue(HttpStatusCode.BadRequest, """{"message":"bad code"}""");

        using var client = ClientFactory.Create(handler);

        var ex = await Assert.ThrowsAsync<MobiscrollConnectException>(
            () => client.Auth.GetTokenAsync("bad-code"));
        Assert.Contains("Error exchanging code for token", ex.Message);
    }

    [Fact]
    public async Task GetConnectionStatusAsync_FallsBackToLegacyPathOn404()
    {
        var statusJson = """
        {"connections":{"google":[{"id":"u@g.com"}],"microsoft":[],"apple":[],"caldav":[]},"limitReached":false}
        """;

        var handler = new FakeHttpMessageHandler()
            .Enqueue(HttpStatusCode.NotFound, """{"message":"not found"}""")
            .Enqueue(HttpStatusCode.OK, statusJson);

        using var client = ClientFactory.Create(handler);
        client.SetCredentials(new TokenResponse { AccessToken = "at", RefreshToken = "rt" });

        var status = await client.Auth.GetConnectionStatusAsync();

        Assert.False(status.LimitReached);
        Assert.Single(status.Connections.Google);
        Assert.Equal("u@g.com", status.Connections.Google[0].Id);

        Assert.EndsWith("/oauth/connection-status", handler.Requests[0].Uri.AbsolutePath);
        Assert.EndsWith("/connection-status", handler.Requests[1].Uri.AbsolutePath);
    }

    [Fact]
    public async Task DisconnectAsync_PostsEmptyBodyWithProviderAndAccountInQuery()
    {
        var handler = new FakeHttpMessageHandler()
            .Enqueue(HttpStatusCode.OK, """{"success":true,"message":"ok"}""");

        using var client = ClientFactory.Create(handler);
        client.SetCredentials(new TokenResponse { AccessToken = "at" });

        var result = await client.Auth.DisconnectAsync(new DisconnectParams
        {
            Provider = Provider.Google,
            Account = "u@example.com",
        });

        Assert.True(result.Success);
        var req = handler.Requests[0];
        Assert.Equal(HttpMethod.Post, req.Method);
        Assert.Contains("provider=google", req.Uri.Query);
        Assert.Contains("account=u%40example.com", req.Uri.Query);
        Assert.Equal("{}", req.Body);
    }
}
