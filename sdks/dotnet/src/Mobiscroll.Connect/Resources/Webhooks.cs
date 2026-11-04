using System;
using System.Collections.Generic;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Threading;
using System.Threading.Tasks;
using Mobiscroll.Connect.Exceptions;
using Mobiscroll.Connect.Internal;
using Mobiscroll.Connect.Models;

namespace Mobiscroll.Connect.Resources;

/// <summary>Calendar change notifications: subscribe, unsubscribe, and verify deliveries.</summary>
public sealed class Webhooks
{
    private readonly ApiClient _client;

    internal Webhooks(ApiClient client)
    {
        _client = client;
    }

    /// <summary>
    /// Subscribe to webhook notifications for a calendar. The server auto-generates
    /// <see cref="WebhookSubscribeData.ChannelId"/> when it is omitted. Pass the calendar's real ID from
    /// <see cref="Calendars.ListAsync"/>; an alias such as <c>primary</c> creates a duplicate subscription.
    /// </summary>
    /// <exception cref="MobiscrollConnectException">
    /// A <see cref="WebhookSubscribeData.ChannelId"/> you supplied is already in use by another user (HTTP 409).
    /// </exception>
    public async Task<WebhookSubscribeResponse> SubscribeWebhookAsync(WebhookSubscribeData data, CancellationToken ct = default)
    {
        if (data is null)
        {
            throw new ArgumentNullException(nameof(data));
        }
        var result = await _client.PostJsonAsync<WebhookSubscribeResponse>("/subscribe-webhook", data, ct)
            .ConfigureAwait(false);
        return result ?? throw new InvalidOperationException("Webhook subscription returned no body");
    }

    /// <summary>
    /// Unsubscribe from webhook notifications for a calendar. Returns <c>Success: true</c>
    /// even when the provider-side subscription had already expired — check
    /// <see cref="WebhookUnsubscribeResponse.Message"/> for details in that case; treat the
    /// response as final regardless. Use the current channel ID: Connect replaces it when it renews a
    /// subscription, so take it from the latest delivery's <see cref="WebhookDeliveryMetadata.ChannelId"/>.
    /// </summary>
    public async Task<WebhookUnsubscribeResponse> UnsubscribeWebhookAsync(WebhookUnsubscribeData data, CancellationToken ct = default)
    {
        if (data is null)
        {
            throw new ArgumentNullException(nameof(data));
        }
        var result = await _client.PostJsonAsync<WebhookUnsubscribeResponse>("/unsubscribe-webhook", data, ct)
            .ConfigureAwait(false);
        return result ?? throw new InvalidOperationException("Webhook unsubscription returned no body");
    }

    /// <summary>
    /// Verify that a webhook delivery came from Mobiscroll Connect, and parse it.
    /// </summary>
    /// <remarks>
    /// Fetches the public keys from <c>/.well-known/webhook-keys</c> on first use and caches them
    /// for the whole process, refreshing them as the endpoint's <c>Cache-Control</c> allows. When
    /// no signature matches, it re-fetches the keys once (at most once a minute) before rejecting,
    /// so a key rotation never rejects genuine deliveries.
    /// <see cref="MobiscrollConnectConfig.WebhookPublicKey"/> is used only when the endpoint cannot
    /// be reached.
    /// </remarks>
    /// <param name="payload">The raw request body, exactly as received; a <c>byte[]</c> converts implicitly.</param>
    /// <param name="headers">The request headers, any casing.</param>
    /// <param name="ct">Cancels waiting for the keys; a fetch shared with other callers keeps running.</param>
    /// <returns>The parsed delivery.</returns>
    /// <exception cref="WebhookVerificationException">
    /// The delivery is not verified. Respond with 503 when <see cref="WebhookVerificationException.Reason"/> is
    /// <see cref="WebhookVerificationReason.NoPublicKeys"/> (Connect retries) and with 401 otherwise (Connect does not retry).
    /// </exception>
    public Task<WebhookDelivery> VerifyWebhookAsync(
        ReadOnlyMemory<byte> payload,
        IEnumerable<KeyValuePair<string, string>> headers,
        CancellationToken ct = default)
        => VerifyCoreAsync(payload, WebhookSignatureHeaders.From(headers), ct);

    /// <inheritdoc cref="VerifyWebhookAsync(ReadOnlyMemory{byte}, IEnumerable{KeyValuePair{string, string}}, CancellationToken)"/>
    /// <param name="payload">The raw request body, exactly as received; not re-serialized JSON.</param>
    /// <param name="headers">The request headers, any casing.</param>
    /// <param name="ct">Cancels waiting for the keys; a fetch shared with other callers keeps running.</param>
    public Task<WebhookDelivery> VerifyWebhookAsync(
        string payload,
        IEnumerable<KeyValuePair<string, string>> headers,
        CancellationToken ct = default)
        => VerifyCoreAsync(WebhookVerifier.EncodePayload(payload), WebhookSignatureHeaders.From(headers), ct);

    /// <inheritdoc cref="VerifyWebhookAsync(ReadOnlyMemory{byte}, IEnumerable{KeyValuePair{string, string}}, CancellationToken)"/>
    /// <param name="payload">The raw request body, exactly as received; a <c>byte[]</c> converts implicitly.</param>
    /// <param name="headers">
    /// Multi-valued request headers, any casing, such as <see cref="System.Net.Http.Headers.HttpHeaders"/>
    /// or ASP.NET Core's <c>IHeaderDictionary</c>. The first value of each header is used.
    /// </param>
    /// <param name="ct">Cancels waiting for the keys; a fetch shared with other callers keeps running.</param>
    public Task<WebhookDelivery> VerifyWebhookAsync<TValues>(
        ReadOnlyMemory<byte> payload,
        IEnumerable<KeyValuePair<string, TValues>> headers,
        CancellationToken ct = default)
        where TValues : IEnumerable<string?>
        => VerifyCoreAsync(payload, WebhookSignatureHeaders.From(headers), ct);

    /// <inheritdoc cref="VerifyWebhookAsync(ReadOnlyMemory{byte}, IEnumerable{KeyValuePair{string, string}}, CancellationToken)"/>
    /// <param name="payload">The raw request body, exactly as received; not re-serialized JSON.</param>
    /// <param name="headers">
    /// Multi-valued request headers, any casing, such as <see cref="System.Net.Http.Headers.HttpHeaders"/>
    /// or ASP.NET Core's <c>IHeaderDictionary</c>. The first value of each header is used.
    /// </param>
    /// <param name="ct">Cancels waiting for the keys; a fetch shared with other callers keeps running.</param>
    public Task<WebhookDelivery> VerifyWebhookAsync<TValues>(
        string payload,
        IEnumerable<KeyValuePair<string, TValues>> headers,
        CancellationToken ct = default)
        where TValues : IEnumerable<string?>
        => VerifyCoreAsync(WebhookVerifier.EncodePayload(payload), WebhookSignatureHeaders.From(headers), ct);

    private async Task<WebhookDelivery> VerifyCoreAsync(
        ReadOnlyMemory<byte> payload,
        WebhookSignatureHeaders headers,
        CancellationToken ct)
    {
        var keysUrl = new Uri(new Uri(_client.BaseUrl), WebhookVerifier.KeysPath).AbsoluteUri;
        var store = WebhookKeyStore.ForUrl(keysUrl);

        var keys = await store.GetKeysAsync(_client.Http, ct).ConfigureAwait(false);
        try
        {
            Verify(payload, headers, keys);
        }
        catch (WebhookVerificationException ex) when (
            ex.Reason is WebhookVerificationReason.NoMatchingSignature or WebhookVerificationReason.NoPublicKeys)
        {
            if (!await store.TryRefreshAsync(_client.Http, ct).ConfigureAwait(false))
            {
                throw;
            }
            keys = await store.GetKeysAsync(_client.Http, ct).ConfigureAwait(false);
            Verify(payload, headers, keys);
        }

        WebhookDelivery? delivery;
        try
        {
            var root = JsonNode.Parse(payload.Span);
            if (root is JsonObject deliveryObject)
            {
                ConvertConferences(deliveryObject);
            }
            delivery = root.Deserialize<WebhookDelivery>(ApiClient.JsonOptions);
        }
        catch (Exception ex) when (ex is JsonException or ArgumentException or NotSupportedException)
        {
            throw InvalidJson();
        }
        return delivery ?? throw InvalidJson();
    }

    private void Verify(ReadOnlyMemory<byte> payload, WebhookSignatureHeaders headers, IReadOnlyList<string> keys)
    {
        var pinnedKey = _client.Config.WebhookPublicKey;
        var candidates = keys.Count > 0 || string.IsNullOrEmpty(pinnedKey) ? keys : new[] { pinnedKey };
        var options = new WebhookVerificationOptions
        {
            Now = WebhookKeyStore.Clock.GetUtcNow().ToUnixTimeSeconds(),
        };
        WebhookVerifier.Verify(payload.Span, headers, candidates, options);
    }

    /// <summary>
    /// Converts each event's <c>conference</c> object, as deliveries carry it, to what <see cref="CalendarEvent"/>
    /// holds: <c>conference</c> is the join URL and <c>conferenceData</c> the REST shape. Strings and
    /// <c>null</c> are left as they are, and an event's own non-null <c>conferenceData</c> is never replaced.
    /// </summary>
    /// <exception cref="JsonException"><c>conference</c> is neither a string, an object nor <c>null</c>.</exception>
    private static void ConvertConferences(JsonObject delivery)
    {
        if (delivery["events"] is not JsonArray events)
        {
            return;
        }
        var deliveryProvider = (delivery["provider"] as JsonValue)?.TryGetValue<string>(out var dp) == true ? dp : null;
        foreach (var node in events)
        {
            if (node is not JsonObject evt || !evt.TryGetPropertyValue("conference", out var conference) || conference is null)
            {
                continue;
            }
            if (conference is JsonValue value)
            {
                if (value.GetValueKind() == JsonValueKind.String)
                {
                    continue;
                }
                throw new JsonException("conference must be a string or an object");
            }
            if (conference is not JsonObject conferenceObject)
            {
                throw new JsonException("conference must be a string or an object");
            }

            var url = (conferenceObject["url"] as JsonValue)?.TryGetValue<string>(out var u) == true ? u : null;
            evt["conference"] = string.IsNullOrEmpty(url) ? null : JsonValue.Create(url);
            if (evt["conferenceData"] is not null)
            {
                continue;
            }

            var eventProvider = (evt["provider"] as JsonValue)?.TryGetValue<string>(out var ep) == true ? ep : null;
            var conferenceProvider = (conferenceObject["provider"] as JsonValue)?.TryGetValue<string>(out var cp) == true ? cp : null;
            if (string.IsNullOrEmpty(conferenceProvider))
            {
                conferenceProvider = InferConferenceProvider(string.IsNullOrEmpty(eventProvider) ? deliveryProvider : eventProvider);
            }

            var conferenceData = new JsonObject();
            if (!string.IsNullOrEmpty(conferenceProvider))
            {
                conferenceData["provider"] = conferenceProvider;
            }
            if (conferenceObject["autoGenerate"] is JsonValue auto
                && auto.GetValueKind() is JsonValueKind.True or JsonValueKind.False)
            {
                conferenceData["autoGenerate"] = auto.GetValue<bool>();
            }
            if (conferenceObject["data"] is JsonObject data)
            {
                foreach (var (key, item) in data)
                {
                    conferenceData[key] = item?.DeepClone();
                }
            }
            if (conferenceData.Count > 0)
            {
                evt["conferenceData"] = conferenceData;
            }
        }
    }

    private static string? InferConferenceProvider(string? calendarProvider)
        => calendarProvider?.ToLowerInvariant() switch
        {
            "google" => "google-meet",
            "microsoft" => "teams",
            "apple" or "caldav" => "caldav",
            _ => null,
        };

    private static WebhookVerificationException InvalidJson()
        => new("Webhook payload is not valid JSON", WebhookVerificationReason.InvalidPayload);
}
