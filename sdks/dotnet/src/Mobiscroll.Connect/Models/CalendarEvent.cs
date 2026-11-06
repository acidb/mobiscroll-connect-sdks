using System;
using System.Collections.Generic;
using System.Text.Json;

namespace Mobiscroll.Connect.Models;

/// <summary>
/// A calendar event. Not sealed: the deprecated <see cref="WebhookEvent"/> extends it.
/// <see cref="ChangeType"/> is set only on events in a <see cref="WebhookDelivery"/>. Deleted Microsoft, Apple and
/// CalDAV events arrive there as placeholders without <see cref="Original"/>; rely only on <see cref="Id"/>,
/// <see cref="Provider"/>, <see cref="CalendarId"/> and <see cref="ChangeType"/> for them.
/// </summary>
public class CalendarEvent
{
    public Provider Provider { get; set; }
    public string Id { get; set; } = string.Empty;
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
    /// <summary>The join URL of the event's conference.</summary>
    public string? Conference { get; set; }

    /// <summary>The conference details; see <see cref="Models.ConferenceData"/>.</summary>
    public ConferenceData? ConferenceData { get; set; }

    /// <summary>"busy" | "free"</summary>
    public string? Availability { get; set; }

    /// <summary>"public" | "private" | "confidential"</summary>
    public string? Privacy { get; set; }

    /// <summary>"confirmed" | "tentative" | "cancelled"</summary>
    public string? Status { get; set; }

    /// <summary>ISO 8601 timestamp of the last modification, e.g. "2026-03-10T13:36:08.000Z".</summary>
    public string? LastModified { get; set; }

    public string? Link { get; set; }

    /// <summary>Raw provider event payload.</summary>
    public JsonElement? Original { get; set; }

    /// <summary>"created" | "updated" | "deleted"; set only on events in a <see cref="WebhookDelivery"/>.</summary>
    public string? ChangeType { get; set; }
}
