using System.Net;
using System.Threading.Tasks;
using Mobiscroll.Connect.Exceptions;
using Mobiscroll.Connect.Models;
using Mobiscroll.Connect.Tests.TestHelpers;

namespace Mobiscroll.Connect.Tests;

public class ApiClientTests
{
    [Fact]
    public void Constructor_ThrowsWhenRequiredConfigMissing()
    {
        Assert.Throws<MobiscrollConnectException>(() =>
            new MobiscrollConnectClient(new MobiscrollConnectConfig()));

        Assert.Throws<MobiscrollConnectException>(() =>
            new MobiscrollConnectClient(new MobiscrollConnectConfig
            {
                ClientId = "x",
                ClientSecret = "",
                RedirectUri = "y",
            }));
    }

    [Fact]
    public async Task On401_WithRefreshToken_RefreshesAndRetriesWithNewBearer()
    {
        var handler = new FakeHttpMessageHandler()
            .Enqueue(HttpStatusCode.Unauthorized, """{"message":"token expired"}""")
            .Enqueue(HttpStatusCode.OK, """{"access_token":"new-at","token_type":"Bearer","expires_in":3600}""")
            .Enqueue(HttpStatusCode.OK, "[]");

        using var client = ClientFactory.Create(handler);
        client.SetCredentials(new TokenResponse
        {
            AccessToken = "old-at",
            RefreshToken = "rt-keep",
        });

        var refreshed = default(TokenResponse);
        client.OnTokensRefreshed(t => refreshed = t);

        await client.Calendars.ListAsync();

        Assert.Equal(3, handler.Requests.Count);

        // First attempt used the old token.
        Assert.Equal("Bearer old-at", handler.Requests[0].Headers["Authorization"]);

        // Refresh call hit /oauth/token with a form body.
        Assert.EndsWith("/oauth/token", handler.Requests[1].Uri.AbsolutePath);
        Assert.Contains("grant_type=refresh_token", handler.Requests[1].Body);

        // Retry used the new bearer.
        Assert.Equal("Bearer new-at", handler.Requests[2].Headers["Authorization"]);

        // Old refresh token preserved because refresh response didn't issue a new one.
        Assert.Equal("rt-keep", client.ApiClient.Credentials?.RefreshToken);

        // Callback fired with updated tokens.
        Assert.NotNull(refreshed);
        Assert.Equal("new-at", refreshed!.AccessToken);
        Assert.Equal("rt-keep", refreshed.RefreshToken);
    }

    [Fact]
    public async Task On401_WithoutRefreshToken_ThrowsAuthenticationException()
    {
        var handler = new FakeHttpMessageHandler()
            .Enqueue(HttpStatusCode.Unauthorized, """{"message":"no token"}""");

        using var client = ClientFactory.Create(handler);
        client.SetCredentials(new TokenResponse { AccessToken = "at" });

        await Assert.ThrowsAsync<AuthenticationException>(() => client.Calendars.ListAsync());
    }

    [Fact]
    public async Task On401_RefreshFailure_ThrowsAuthenticationException()
    {
        var handler = new FakeHttpMessageHandler()
            .Enqueue(HttpStatusCode.Unauthorized, """{"message":"token expired"}""")
            .Enqueue(HttpStatusCode.BadRequest, """{"message":"invalid_grant"}""");

        using var client = ClientFactory.Create(handler);
        client.SetCredentials(new TokenResponse { AccessToken = "at", RefreshToken = "bad-rt" });

        await Assert.ThrowsAsync<AuthenticationException>(() => client.Calendars.ListAsync());
    }
}
