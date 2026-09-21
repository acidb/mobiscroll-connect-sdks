namespace Mobiscroll.Connect.Models;

/// <summary>Request body for <see cref="Resources.Webhooks.UnsubscribeWebhookAsync"/>.</summary>
public sealed class WebhookUnsubscribeData
{
    public Provider Provider { get; set; }
    public string ChannelId { get; set; } = string.Empty;

    /// <summary>Required by some providers (e.g. Google) to fully unsubscribe.</summary>
    public string? ResourceId { get; set; }
}
