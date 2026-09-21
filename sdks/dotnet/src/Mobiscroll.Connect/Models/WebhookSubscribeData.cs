namespace Mobiscroll.Connect.Models;

/// <summary>Request body for <see cref="Resources.Webhooks.SubscribeWebhookAsync"/>.</summary>
public sealed class WebhookSubscribeData
{
    public Provider Provider { get; set; }
    public string CalendarId { get; set; } = string.Empty;

    /// <summary>Auto-generated server-side when omitted.</summary>
    public string? ChannelId { get; set; }

    /// <summary>Provider-specific expiration timestamp (ms epoch).</summary>
    public long? Expiration { get; set; }
}
