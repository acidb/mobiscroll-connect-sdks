using System;
using System.Collections.Generic;
using System.Text.Json;
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
    /// <see cref="WebhookSubscribeData.ChannelId"/> when it is omitted.
    /// </summary>
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
    /// response as final regardless.
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
    /// <exception cref="WebhookVerificationException">The delivery is not genuine; respond with a 4xx.</exception>
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
            delivery = JsonSerializer.Deserialize<WebhookDelivery>(payload.Span, ApiClient.JsonOptions);
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

    private static WebhookVerificationException InvalidJson()
        => new("Webhook payload is not valid JSON", WebhookVerificationReason.InvalidPayload);
}
