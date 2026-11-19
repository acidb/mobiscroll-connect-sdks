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

    /// <summary>
    /// The channel's webhook mode, named like <see cref="EventListParams.SingleEvents"/>. <c>false</c> is series
    /// mode: for a Google calendar, a changed recurring series is delivered as its master event instead of
    /// every occurrence. <c>true</c> delivers every occurrence. Outlook, iCloud and CalDAV deliver the series
    /// master in both modes. When <c>null</c> it is omitted from the body: the channel keeps the mode of the
    /// subscription it replaces, otherwise the project's setting (set on the dashboard) applies. An explicit
    /// <c>false</c> is sent.
    /// </summary>
    public bool? SingleEvents { get; set; }
}
