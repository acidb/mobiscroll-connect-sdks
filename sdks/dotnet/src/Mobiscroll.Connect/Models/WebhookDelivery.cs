using System;
using System.Collections.Generic;
using System.Text.Json;

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

/// <summary>
/// A changed event in a <see cref="WebhookDelivery"/>. It has the fields of <see cref="CalendarEvent"/>, except
/// that <see cref="Conference"/> is a <see cref="WebhookConference"/> object, as deliveries carry it.
/// Deleted Microsoft, Apple and CalDAV events arrive as placeholders without <see cref="CalendarId"/> and
/// <see cref="Original"/>; rely only on <see cref="Id"/>, <see cref="Provider"/> and <see cref="ChangeType"/> for a
/// deleted event.
/// </summary>
public sealed class WebhookEvent
{
    public Provider Provider { get; set; }
    public string Id { get; set; } = string.Empty;

    /// <summary>Empty on deleted placeholders.</summary>
    public string CalendarId { get; set; } = string.Empty;

    public string Title { get; set; } = string.Empty;

    /// <summary>Event description or notes.</summary>
    public string? Description { get; set; }

    public DateTime Start { get; set; }
    public DateTime End { get; set; }
    public bool AllDay { get; set; }
    public string? RecurringEventId { get; set; }
    public string? Color { get; set; }
    public string? Location { get; set; }
    public List<EventAttendee>? Attendees { get; set; }
    public Dictionary<string, JsonElement>? Custom { get; set; }

    /// <summary>Conference details; <c>null</c> when the event has none.</summary>
    public WebhookConference? Conference { get; set; }

    /// <summary>"busy" | "free"</summary>
    public string? Availability { get; set; }

    /// <summary>"public" | "private" | "confidential"</summary>
    public string? Privacy { get; set; }

    /// <summary>"confirmed" | "tentative" | "cancelled"</summary>
    public string? Status { get; set; }

    /// <summary>
    /// ISO 8601 timestamp of the last modification; for a deleted placeholder, when Connect detected the deletion.
    /// </summary>
    public string? LastModified { get; set; }

    public string? Link { get; set; }

    /// <summary>Raw provider event payload; <c>null</c> on deleted placeholders.</summary>
    public JsonElement? Original { get; set; }

    /// <summary>"created" | "updated" | "deleted"</summary>
    public string? ChangeType { get; set; }
}

/// <summary>
/// Conference details on a <see cref="WebhookEvent"/>. Deliveries carry an object here, while
/// <see cref="Resources.Events.ListAsync"/> returns the join URL as a string in <see cref="CalendarEvent.Conference"/>.
/// </summary>
public sealed class WebhookConference
{
    /// <summary>Conference join URL.</summary>
    public string Url { get; set; } = string.Empty;

    /// <summary>Conference provider as the calendar provider reports it, e.g. "hangoutsMeet", "teams".</summary>
    public string? Provider { get; set; }

    /// <summary>Same value as <see cref="Provider"/>.</summary>
    public string? MeetingProvider { get; set; }

    /// <summary>Provider-specific conference payload, as raw <see cref="JsonElement"/> values.</summary>
    public Dictionary<string, JsonElement>? Data { get; set; }
}

/// <summary>The <c>metadata</c> object of a <see cref="WebhookDelivery"/>.</summary>
public sealed class WebhookDeliveryMetadata
{
    public string ChannelId { get; set; } = string.Empty;
    public int EventCount { get; set; }
    public bool? IsInitialSync { get; set; }
}
