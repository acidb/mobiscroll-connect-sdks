namespace Mobiscroll.Connect.Models;

/// <summary>The <c>subscription</c> object returned by <see cref="Resources.Webhooks.SubscribeWebhookAsync"/>.</summary>
public sealed class WebhookSubscription
{
    public string ChannelId { get; set; } = string.Empty;

    /// <summary>Present for Google.</summary>
    public string? ResourceId { get; set; }

    /// <summary>ISO 8601 timestamp; present for some providers.</summary>
    public string? Expiration { get; set; }
}

public sealed class WebhookSubscribeResponse
{
    public bool Success { get; set; }
    public string Provider { get; set; } = string.Empty;
    public WebhookSubscription Subscription { get; set; } = new();
    public string ServerWebhookUrl { get; set; } = string.Empty;
    public string ChannelId { get; set; } = string.Empty;

    /// <summary>
    /// The mode the channel was created with: <c>false</c> is series mode, <c>true</c> is every occurrence.
    /// <c>null</c> when the response does not carry it (unknown, not series mode).
    /// </summary>
    public bool? SingleEvents { get; set; }
}
