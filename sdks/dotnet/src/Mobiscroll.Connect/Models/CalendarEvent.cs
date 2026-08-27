using System;
using System.Collections.Generic;
using System.Text.Json;

namespace Mobiscroll.Connect.Models;

public sealed class CalendarEvent
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
    public string? Conference { get; set; }

    /// <summary>
    /// Provider-specific conference metadata; use it for details beyond <see cref="Conference"/>.
    /// Keys vary per provider, so the payload is left as raw <see cref="JsonElement"/> values.
    /// </summary>
    public Dictionary<string, JsonElement>? ConferenceData { get; set; }

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
}
