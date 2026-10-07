using System.Collections.Generic;

namespace Mobiscroll.Connect.Models;

/// <summary>
/// A verified webhook delivery sent by Mobiscroll Connect to the project's webhook URL.
/// Returned by <see cref="Resources.Webhooks.VerifyWebhookAsync(System.ReadOnlyMemory{byte}, IEnumerable{KeyValuePair{string, string}}, System.Threading.CancellationToken)"/>.
/// </summary>
public sealed class WebhookDelivery
{
    public Provider Provider { get; set; }
    public string UserId { get; set; } = string.Empty;
    public string CalendarId { get; set; } = string.Empty;
    public List<WebhookEvent> Events { get; set; } = new();

    /// <summary>"created" | "updated" | "deleted" | "mixed"</summary>
    public string? ChangeType { get; set; }

    /// <summary>ISO 8601 timestamp of when Connect processed the change.</summary>
    public string Timestamp { get; set; } = string.Empty;

    public WebhookDeliveryMetadata Metadata { get; set; } = new();
}

/// <summary>A changed event in a <see cref="WebhookDelivery"/>.</summary>
public sealed class WebhookEvent : CalendarEvent
{
    /// <summary>"created" | "updated" | "deleted"</summary>
    public string? ChangeType { get; set; }
}

/// <summary>The <c>metadata</c> object of a <see cref="WebhookDelivery"/>.</summary>
public sealed class WebhookDeliveryMetadata
{
    public string ChannelId { get; set; } = string.Empty;
    public int EventCount { get; set; }
    public bool? IsInitialSync { get; set; }
}
