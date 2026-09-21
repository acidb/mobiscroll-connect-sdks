using System;
using System.Threading;
using System.Threading.Tasks;
using Mobiscroll.Connect.Models;

namespace Mobiscroll.Connect.Resources;

/// <summary>Subscribe to and unsubscribe from calendar change notifications.</summary>
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
}
