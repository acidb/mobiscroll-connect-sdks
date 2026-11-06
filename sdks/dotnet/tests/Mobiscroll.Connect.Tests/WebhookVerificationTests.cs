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

    [Fact]
    public async Task VerifyWebhookAsync_ParsesADeliveryWithAConferenceUrl()
    {
        var vector = WebhookVectors.ByName("delivery: Google events with and without a conference");
        using var client = ClientFactory.Create(EnqueueKeys(new FakeHttpMessageHandler(), WebhookVectors.Key("active")));

        var delivery = await client.Webhooks.VerifyWebhookAsync(vector.Body, vector.Headers);

        var meeting = delivery.Events[0];
        Assert.Equal("https://meet.google.com/abc-defg-hij", meeting.Conference);
        Assert.Equal("hangoutsMeet", meeting.ConferenceData!.Provider);
        Assert.Equal("abc-defg-hij", meeting.ConferenceData.Original!["conferenceId"].GetString());
        Assert.Equal(new DateTime(2026, 9, 22, 9, 0, 0, DateTimeKind.Utc), meeting.Start.ToUniversalTime());
        Assert.Equal("2026-09-21T09:58:12.000Z", meeting.LastModified);
        Assert.Equal("updated", meeting.ChangeType);
        Assert.True(meeting.Attendees![0].Organizer);
        Assert.Null(delivery.Events[1].Conference);
        Assert.Null(delivery.Events[1].ConferenceData);
        Assert.True(delivery.Events[1].AllDay);
        Assert.Equal("chan_google_1", delivery.Metadata.ChannelId);
    }

    [Fact]
    public async Task VerifyWebhookAsync_ParsesADeletedPlaceholderWithoutCalendarId()
    {
        var vector = WebhookVectors.ByName("delivery: Microsoft Teams event and a deleted placeholder");
        using var client = ClientFactory.Create(EnqueueKeys(new FakeHttpMessageHandler(), WebhookVectors.Key("active")));

        var delivery = await client.Webhooks.VerifyWebhookAsync(vector.Body, vector.Headers);

        Assert.Equal("https://teams.microsoft.com/l/meetup-join/19%3ameeting_abc", delivery.Events[0].Conference);
        Assert.Equal("teams", delivery.Events[0].ConferenceData!.Provider);
        var deleted = delivery.Events[1];
        Assert.Equal("deleted", deleted.ChangeType);
        Assert.Equal(string.Empty, deleted.CalendarId);
        Assert.Null(deleted.Original);
    }

    [Fact]
    public async Task VerifyWebhookAsync_KeepsTheApi161ConferenceDataAndCalendarIdOnDeletedEvents()
    {
        async Task<WebhookDelivery> Verify(string name)
        {
            var vector = WebhookVectors.ByName(name);
            using var client = ClientFactory.Create(EnqueueKeys(new FakeHttpMessageHandler(), WebhookVectors.Key("active")));
            return await client.Webhooks.VerifyWebhookAsync(vector.Body, vector.Headers);
        }

        var google = await Verify("delivery: 1.6.1 Google Meet event with conferenceData and a cancelled event with calendarId");
        Assert.Equal("https://meet.google.com/abc-defg-hij", google.Events[0].Conference);
        Assert.Equal("hangoutsMeet", google.Events[0].ConferenceData!.Provider);
        Assert.Equal("https://meet.google.com/abc-defg-hij", google.Events[0].ConferenceData!.Url);
        Assert.Equal("abc-defg-hij", google.Events[0].ConferenceData!.Original!["conferenceId"].GetString());
        Assert.Equal("work@company.com", google.Events[1].CalendarId);

        var microsoft = await Verify("delivery: 1.6.1 Microsoft Teams event with conferenceData and a deleted placeholder with calendarId");
        Assert.Equal("teams", microsoft.Events[0].ConferenceData!.Provider);
        Assert.Equal("123456789", microsoft.Events[0].ConferenceData!.Original!["conferenceId"].GetString());
        Assert.Equal("AAMkAGI2Y2FsAAA=", microsoft.Events[1].CalendarId);

        var apple = await Verify("delivery: 1.6.1 Apple event with a URL as conference and a deleted placeholder with calendarId");
        Assert.Equal("https://example.com/room/42", apple.Events[0].Conference);
        Assert.Equal("caldav", apple.Events[0].ConferenceData!.Provider);
        Assert.Equal("https://example.com/room/42", apple.Events[0].ConferenceData!.Url);
        Assert.Null(apple.Events[0].ConferenceData!.Original);
        Assert.Equal("https://caldav.icloud.com/1234567/calendars/work/", apple.Events[1].CalendarId);
    }

    private async Task<WebhookDelivery> VerifySignedAsync(string eventsJson, string deliveryProvider = "google")
    {
        WebhookKeyStore.Reset();
        WebhookKeyStore.Clock = _clock;
        using var key = Key.Create(SignatureAlgorithm.Ed25519);
        var publicKey = "whpk_" + Convert.ToBase64String(key.PublicKey.Export(KeyBlobFormat.RawPublicKey));
        var body = "{\"provider\":\"" + deliveryProvider + "\",\"userId\":\"u1\",\"calendarId\":\"c1\",\"timestamp\":\"2026-09-21T10:00:00.000Z\","
            + "\"events\":[" + eventsJson + "],\"metadata\":{\"channelId\":\"ch1\",\"eventCount\":1,\"isInitialSync\":false}}";
        var timestamp = _valid.Now.ToString(System.Globalization.CultureInfo.InvariantCulture);
        var signature = SignatureAlgorithm.Ed25519.Sign(key, Encoding.UTF8.GetBytes($"msg_1.{timestamp}.{body}"));
        var headers = new Dictionary<string, string>
        {
            ["webhook-id"] = "msg_1",
            ["webhook-timestamp"] = timestamp,
            ["webhook-signature"] = "v1a," + Convert.ToBase64String(signature),
        };
        using var client = ClientFactory.Create(EnqueueKeys(new FakeHttpMessageHandler(), publicKey));
        return await client.Webhooks.VerifyWebhookAsync(body, headers);
    }

    private static string EventJson(string provider, string? extra)
        => "{\"id\":\"e1\",\"provider\":\"" + provider + "\",\"calendarId\":\"c1\",\"title\":\"A\",\"start\":\"2026-09-21T10:00:00.000Z\","
            + "\"end\":\"2026-09-21T11:00:00.000Z\",\"allDay\":false,\"changeType\":\"updated\"" + (extra is null ? "" : "," + extra) + "}";

    [Fact]
    public async Task VerifyWebhookAsync_ReadsAStringConference()
    {
        var evt = (await VerifySignedAsync(EventJson("google", "\"conference\":\"https://meet.test/s\""))).Events[0];

        Assert.Equal("https://meet.test/s", evt.Conference);
        Assert.Null(evt.ConferenceData);
    }

    [Theory]
    [InlineData("")]
    [InlineData("\"conference\":null")]
    public async Task VerifyWebhookAsync_AcceptsANullOrAbsentConference(string extra)
    {
        var evt = (await VerifySignedAsync(EventJson("google", extra.Length == 0 ? null : extra))).Events[0];

        Assert.Null(evt.Conference);
        Assert.Null(evt.ConferenceData);
    }

    [Theory]
    [InlineData("5")]
    [InlineData("true")]
    [InlineData("[1,2]")]
    [InlineData("[]")]
    [InlineData("{\"url\":\"https://x.test/j\"}")]
    public async Task VerifyWebhookAsync_RejectsAConferenceThatIsNotAString(string conference)
    {
        var ex = await Assert.ThrowsAsync<WebhookVerificationException>(
            () => VerifySignedAsync(EventJson("google", "\"conference\":" + conference)));

        Assert.Equal(WebhookVerificationReason.InvalidPayload, ex.Reason);
    }

    [Fact]
    public async Task VerifyWebhookAsync_ReadsConferenceDataWithItsFourFields()
    {
        var evt = (await VerifySignedAsync(EventJson("google",
            "\"conference\":\"https://x.test/j\",\"conferenceData\":{\"provider\":\"teams\",\"url\":\"https://x.test/j\","
            + "\"autoGenerate\":true,\"original\":{\"a\":1}}"))).Events[0];

        Assert.Equal("https://x.test/j", evt.Conference);
        Assert.Equal("teams", evt.ConferenceData!.Provider);
        Assert.Equal("https://x.test/j", evt.ConferenceData.Url);
        Assert.True(evt.ConferenceData.AutoGenerate);
        Assert.Equal(1, evt.ConferenceData.Original!["a"].GetInt32());
    }

    [Fact]
    public async Task VerifyWebhookAsync_AcceptsADeletedPlaceholderWithoutCalendarIdOrOriginal()
    {
        var evt = (await VerifySignedAsync("{\"provider\":\"microsoft\",\"id\":\"gone_1\",\"changeType\":\"deleted\"}", "microsoft")).Events[0];

        Assert.Equal("deleted", evt.ChangeType);
        Assert.Equal(string.Empty, evt.CalendarId);
        Assert.Null(evt.Original);
        Assert.Null(evt.Conference);
    }

#pragma warning disable CS0618
    [Fact]
    public void WebhookEvent_IsADeprecatedCalendarEvent()
    {
        Assert.True(typeof(CalendarEvent).IsAssignableFrom(typeof(WebhookEvent)));
        Assert.NotNull(typeof(WebhookEvent).GetCustomAttributes(typeof(ObsoleteAttribute), false).SingleOrDefault());
    }
#pragma warning restore CS0618
}
