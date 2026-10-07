using System;
using System.Collections.Generic;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using Mobiscroll.Connect.Exceptions;
using Mobiscroll.Connect.Internal;
using Mobiscroll.Connect.Models;
using Mobiscroll.Connect.Tests.TestHelpers;
using NSec.Cryptography;

namespace Mobiscroll.Connect.Tests;

/// <summary>
/// <see cref="Resources.Webhooks.VerifyWebhookAsync(ReadOnlyMemory{byte}, IEnumerable{KeyValuePair{string, string}}, CancellationToken)"/>
/// and the process-wide key cache. The cache and clock are static, so every test that touches
/// them lives in this class, whose tests xUnit runs one at a time.
/// </summary>
public sealed class WebhookVerificationTests : IDisposable
{
    private const string KeysUrl = "https://connect.mobiscroll.com/.well-known/webhook-keys";

    private readonly WebhookVector _valid = WebhookVectors.ByName("valid single signature");
    private readonly ManualTimeProvider _clock;

    public WebhookVerificationTests()
    {
        WebhookKeyStore.Reset();
        _clock = new ManualTimeProvider(DateTimeOffset.FromUnixTimeSeconds(_valid.Now));
        WebhookKeyStore.Clock = _clock;
    }

    public void Dispose()
    {
        WebhookKeyStore.Reset();
        WebhookKeyStore.Clock = TimeProvider.System;
    }

    private static string KeysJson(params string[] keys)
        => "{\"keys\":[" + string.Join(",", keys.Select(k => $"{{\"id\":\"{k}\",\"alg\":\"ed25519\",\"key\":\"{k}\",\"status\":\"active\"}}")) + "]}";

    private static FakeHttpMessageHandler EnqueueKeys(FakeHttpMessageHandler handler, string key, string cacheControl = "public, max-age=3600")
        => handler.Enqueue(HttpStatusCode.OK, KeysJson(key), new Dictionary<string, string> { ["Cache-Control"] = cacheControl });

    private static FakeHttpMessageHandler EnqueueFailure(FakeHttpMessageHandler handler)
        => handler.EnqueueRaw(_ => throw new HttpRequestException("Connection refused"));

    [Fact]
    public async Task VerifyWebhookAsync_FetchesKeysLazilyFromTheApiOriginWithoutAuth()
    {
        var handler = EnqueueKeys(new FakeHttpMessageHandler(), WebhookVectors.Key("active"));
        using var client = ClientFactory.Create(handler);
        client.SetCredentials(new TokenResponse { AccessToken = "at" });
        Assert.Empty(handler.Requests);

        var delivery = await client.Webhooks.VerifyWebhookAsync(_valid.Body, _valid.Headers);

        var request = Assert.Single(handler.Requests);
        Assert.Equal(HttpMethod.Get, request.Method);
        Assert.Equal(KeysUrl, request.Uri.AbsoluteUri);
        Assert.Equal(string.Empty, request.Headers["Authorization"]);
        Assert.Equal("user_42", delivery.UserId);
        Assert.Equal(Provider.Google, delivery.Provider);
        Assert.Equal("primary", delivery.CalendarId);
        Assert.Equal("Café meeting ☕ — Zoë", delivery.Events[0].Title);
        Assert.Equal("2026-09-21T10:00:00.000Z", delivery.Events[0].LastModified);
    }

    [Fact]
    public async Task VerifyWebhookAsync_DerivesTheKeysUrlFromTheConfiguredBaseUrl()
    {
        var handler = EnqueueKeys(new FakeHttpMessageHandler(), WebhookVectors.Key("active"));
        using var client = ClientFactory.Create(handler, "https://connect-dev.example.com:8443/api/");

        await client.Webhooks.VerifyWebhookAsync(_valid.Body, _valid.Headers);

        Assert.Equal("https://connect-dev.example.com:8443/.well-known/webhook-keys", handler.Requests[0].Uri.AbsoluteUri);
    }

    [Fact]
    public async Task VerifyWebhookAsync_SharesTheKeyCacheAcrossClientsAndHonoursMaxAge()
    {
        var handler = new FakeHttpMessageHandler();
        EnqueueKeys(handler, WebhookVectors.Key("active"), "max-age=120");
        EnqueueKeys(handler, WebhookVectors.Key("active"), "max-age=120");

        using (var first = ClientFactory.Create(handler))
        {
            await first.Webhooks.VerifyWebhookAsync(_valid.Body, _valid.Headers);
        }
        using (var second = ClientFactory.Create(handler))
        {
            await second.Webhooks.VerifyWebhookAsync(_valid.Body, _valid.Headers);
        }
        Assert.Single(handler.Requests);

        _clock.Advance(TimeSpan.FromSeconds(121));
        using var third = ClientFactory.Create(handler);
        await third.Webhooks.VerifyWebhookAsync(_valid.Body, _valid.Headers);
        Assert.Equal(2, handler.Requests.Count);
    }

    [Fact]
    public async Task VerifyWebhookAsync_RefetchesOnceAfterARotation()
    {
        var handler = new FakeHttpMessageHandler();
        EnqueueKeys(handler, WebhookVectors.Key("unrelated"));
        EnqueueKeys(handler, WebhookVectors.Key("active"));
        using var client = ClientFactory.Create(handler);

        _clock.Advance(TimeSpan.FromSeconds(-61));
        await WebhookKeyStore.ForUrl(KeysUrl).GetKeysAsync(client.ApiClient.Http, CancellationToken.None);
        _clock.Advance(TimeSpan.FromSeconds(61));

        var delivery = await client.Webhooks.VerifyWebhookAsync(_valid.Body, _valid.Headers);

        Assert.Equal("primary", delivery.CalendarId);
        Assert.Equal(2, handler.Requests.Count);
    }

    [Fact]
    public async Task VerifyWebhookAsync_DoesNotRefetchMoreThanOnceAMinuteOnForgedDeliveries()
    {
        var handler = EnqueueKeys(new FakeHttpMessageHandler(), WebhookVectors.Key("active"));
        using var client = ClientFactory.Create(handler);
        var forged = WebhookVectors.ByName("tampered body");

        var first = await Assert.ThrowsAsync<WebhookVerificationException>(
            () => client.Webhooks.VerifyWebhookAsync(forged.Body, forged.Headers));
        var second = await Assert.ThrowsAsync<WebhookVerificationException>(
            () => client.Webhooks.VerifyWebhookAsync(forged.Body, forged.Headers));

        Assert.Equal(WebhookVerificationReason.NoMatchingSignature, first.Reason);
        Assert.Equal(WebhookVerificationReason.NoMatchingSignature, second.Reason);
        Assert.Single(handler.Requests);
    }

    [Fact]
    public async Task VerifyWebhookAsync_RejectsOtherFailuresWithoutRefetching()
    {
        var handler = EnqueueKeys(new FakeHttpMessageHandler(), WebhookVectors.Key("active"));
        using var client = ClientFactory.Create(handler);
        var stale = WebhookVectors.ByName("timestamp 301 s in the future");
        _clock.Now = DateTimeOffset.FromUnixTimeSeconds(stale.Now);

        var ex = await Assert.ThrowsAsync<WebhookVerificationException>(
            () => client.Webhooks.VerifyWebhookAsync(stale.Body, stale.Headers));

        Assert.Equal(WebhookVerificationReason.TimestampOutOfTolerance, ex.Reason);
        Assert.Single(handler.Requests);
    }

    [Fact]
    public async Task VerifyWebhookAsync_FallsBackToThePinnedKeyWhenTheEndpointCannotBeReached()
    {
        var handler = EnqueueFailure(new FakeHttpMessageHandler());
        using var client = ClientFactory.Create(handler, webhookPublicKey: WebhookVectors.Key("active"));

        var delivery = await client.Webhooks.VerifyWebhookAsync(_valid.Body, _valid.Headers);

        Assert.Equal(Provider.Google, delivery.Provider);
        Assert.Single(handler.Requests);
    }

    [Fact]
    public async Task VerifyWebhookAsync_FallsBackToThePinnedKeyOnAnErrorStatus()
    {
        var handler = new FakeHttpMessageHandler().Enqueue(HttpStatusCode.ServiceUnavailable, """{"message":"down"}""");
        using var client = ClientFactory.Create(handler, webhookPublicKey: WebhookVectors.Key("active"));

        var delivery = await client.Webhooks.VerifyWebhookAsync(_valid.Body, _valid.Headers);

        Assert.Equal("user_42", delivery.UserId);
    }

    [Fact]
    public async Task VerifyWebhookAsync_IgnoresThePinnedKeyWhileFetchedKeysExist()
    {
        var handler = EnqueueKeys(new FakeHttpMessageHandler(), WebhookVectors.Key("unrelated"));
        using var client = ClientFactory.Create(handler, webhookPublicKey: WebhookVectors.Key("active"));

        var ex = await Assert.ThrowsAsync<WebhookVerificationException>(
            () => client.Webhooks.VerifyWebhookAsync(_valid.Body, _valid.Headers));

        Assert.Equal(WebhookVerificationReason.NoMatchingSignature, ex.Reason);
    }

    [Fact]
    public async Task VerifyWebhookAsync_KeepsTheLastGoodKeysWhenARefreshFails()
    {
        var handler = EnqueueKeys(new FakeHttpMessageHandler(), WebhookVectors.Key("active"));
        EnqueueFailure(handler);
        using var client = ClientFactory.Create(handler);
        await client.Webhooks.VerifyWebhookAsync(_valid.Body, _valid.Headers);

        _clock.Advance(TimeSpan.FromSeconds(3601));
        var later = new Dictionary<string, string>(_valid.Headers)
        {
            ["webhook-timestamp"] = (_valid.Now + 3601).ToString(System.Globalization.CultureInfo.InvariantCulture),
        };
        var ex = await Assert.ThrowsAsync<WebhookVerificationException>(
            () => client.Webhooks.VerifyWebhookAsync(_valid.Body, later));

        Assert.Equal(WebhookVerificationReason.NoMatchingSignature, ex.Reason);
        Assert.Equal(2, handler.Requests.Count);
    }

    [Fact]
    public async Task VerifyWebhookAsync_FailsWithNoPublicKeysWhenNothingIsAvailable()
    {
        var handler = EnqueueFailure(new FakeHttpMessageHandler());
        using var client = ClientFactory.Create(handler);

        var ex = await Assert.ThrowsAsync<WebhookVerificationException>(
            () => client.Webhooks.VerifyWebhookAsync(_valid.Body, _valid.Headers));

        Assert.Equal(WebhookVerificationReason.NoPublicKeys, ex.Reason);
        Assert.Single(handler.Requests);
    }

    [Fact]
    public async Task VerifyWebhookAsync_SkipsKeysForOtherAlgorithms()
    {
        var json = $$"""
        {"keys":[
          {"id":"x","alg":"rsa","key":"{{WebhookVectors.Key("active")}}","status":"active"},
          {"id":"y","key":"not-a-whpk-key","status":"active"}
        ]}
        """;
        var handler = new FakeHttpMessageHandler().Enqueue(HttpStatusCode.OK, json);
        using var client = ClientFactory.Create(handler);

        var ex = await Assert.ThrowsAsync<WebhookVerificationException>(
            () => client.Webhooks.VerifyWebhookAsync(_valid.Body, _valid.Headers));

        Assert.Equal(WebhookVerificationReason.NoPublicKeys, ex.Reason);
    }

    [Fact]
    public async Task VerifyWebhookAsync_ConcurrentCallsShareOneFetch()
    {
        var release = new TaskCompletionSource<HttpResponseMessage>(TaskCreationOptions.RunContinuationsAsynchronously);
        var handler = new FakeHttpMessageHandler().EnqueueAsync(_ => release.Task);
        using var client = ClientFactory.Create(handler);

        var calls = Enumerable.Range(0, 3)
            .Select(_ => Task.Run(() => client.Webhooks.VerifyWebhookAsync(_valid.Body, _valid.Headers)))
            .ToArray();
        await Task.Delay(50);
        release.SetResult(new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new StringContent(KeysJson(WebhookVectors.Key("active")), Encoding.UTF8, "application/json"),
        });
        var deliveries = await Task.WhenAll(calls);

        Assert.All(deliveries, d => Assert.Equal("user_42", d.UserId));
        Assert.Single(handler.Requests);
    }

    [Fact]
    public async Task VerifyWebhookAsync_LooksUpHeadersCaseInsensitively()
    {
        var handler = EnqueueKeys(new FakeHttpMessageHandler(), WebhookVectors.Key("active"));
        using var client = ClientFactory.Create(handler);
        using var request = new HttpRequestMessage();
        foreach (var header in _valid.Headers)
        {
            request.Headers.TryAddWithoutValidation(header.Key.ToUpperInvariant(), header.Value);
        }

        var fromHttpHeaders = await client.Webhooks.VerifyWebhookAsync(Encoding.UTF8.GetBytes(_valid.Body), request.Headers);
        var fromUpperCase = await client.Webhooks.VerifyWebhookAsync(
            _valid.Body,
            _valid.Headers.ToDictionary(h => h.Key.ToUpperInvariant(), h => h.Value));

        Assert.Equal("user_42", fromHttpHeaders.UserId);
        Assert.Equal("user_42", fromUpperCase.UserId);
    }

    [Fact]
    public async Task VerifyWebhookAsync_RejectsASignedBodyThatIsNotJson()
    {
        using var key = Key.Create(SignatureAlgorithm.Ed25519);
        var publicKey = "whpk_" + Convert.ToBase64String(key.PublicKey.Export(KeyBlobFormat.RawPublicKey));
        const string body = "not json";
        var timestamp = _valid.Now.ToString(System.Globalization.CultureInfo.InvariantCulture);
        var signature = SignatureAlgorithm.Ed25519.Sign(key, Encoding.UTF8.GetBytes($"msg_1.{timestamp}.{body}"));
        var headers = new Dictionary<string, string>
        {
            ["webhook-id"] = "msg_1",
            ["webhook-timestamp"] = timestamp,
            ["webhook-signature"] = "v1a," + Convert.ToBase64String(signature),
        };
        var handler = EnqueueKeys(new FakeHttpMessageHandler(), publicKey);
        using var client = ClientFactory.Create(handler);

        var ex = await Assert.ThrowsAsync<WebhookVerificationException>(
            () => client.Webhooks.VerifyWebhookAsync(body, headers));

        Assert.Equal(WebhookVerificationReason.InvalidPayload, ex.Reason);
    }

    [Fact]
    public async Task VerifyWebhookAsync_ParsesTheFullDeliveryShape()
    {
        using var key = Key.Create(SignatureAlgorithm.Ed25519);
        var publicKey = "whpk_" + Convert.ToBase64String(key.PublicKey.Export(KeyBlobFormat.RawPublicKey));
        const string body = """
        {"provider":"microsoft","userId":"u1","calendarId":"c1","changeType":"mixed","timestamp":"2026-09-21T10:00:00.000Z",
         "events":[{"id":"e1","provider":"microsoft","title":"A","start":"2026-09-21T10:00:00Z","end":"2026-09-21T11:00:00Z","changeType":"deleted","unknown":1}],
         "metadata":{"channelId":"ch1","eventCount":1,"isInitialSync":false}}
        """;
        var timestamp = _valid.Now.ToString(System.Globalization.CultureInfo.InvariantCulture);
        var signature = SignatureAlgorithm.Ed25519.Sign(key, Encoding.UTF8.GetBytes($"msg_1.{timestamp}.{body}"));
        var headers = new Dictionary<string, string>
        {
            ["webhook-id"] = "msg_1",
            ["webhook-timestamp"] = timestamp,
            ["webhook-signature"] = "v1a," + Convert.ToBase64String(signature),
        };
        var handler = EnqueueKeys(new FakeHttpMessageHandler(), publicKey);
        using var client = ClientFactory.Create(handler);

        var delivery = await client.Webhooks.VerifyWebhookAsync(body, headers);

        Assert.Equal(Provider.Microsoft, delivery.Provider);
        Assert.Equal("mixed", delivery.ChangeType);
        Assert.Equal("2026-09-21T10:00:00.000Z", delivery.Timestamp);
        Assert.Equal("deleted", delivery.Events[0].ChangeType);
        Assert.Equal("e1", delivery.Events[0].Id);
        Assert.Equal("ch1", delivery.Metadata.ChannelId);
        Assert.Equal(1, delivery.Metadata.EventCount);
        Assert.False(delivery.Metadata.IsInitialSync);
    }
}
