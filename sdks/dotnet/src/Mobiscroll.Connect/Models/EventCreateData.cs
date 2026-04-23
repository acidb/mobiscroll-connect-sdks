using System;
using System.Collections.Generic;
using System.Text.Json.Serialization;

namespace Mobiscroll.Connect.Models;

public class EventCreateData
{
    public Provider Provider { get; set; }
    public string CalendarId { get; set; } = string.Empty;
    public string Title { get; set; } = string.Empty;
    public DateTime Start { get; set; }
    public DateTime End { get; set; }

    public string? Description { get; set; }
    public string? Location { get; set; }
    public bool? AllDay { get; set; }

    /// <summary>Attendee email addresses.</summary>
    public List<string>? Attendees { get; set; }

    public RecurrenceRule? Recurrence { get; set; }

    public Dictionary<string, object>? Custom { get; set; }

    /// <summary>"busy" | "free"</summary>
    public string? Availability { get; set; }

    /// <summary>"public" | "private" | "confidential"</summary>
    public string? Privacy { get; set; }

    /// <summary>"confirmed" | "tentative" | "cancelled"</summary>
    public string? Status { get; set; }
}
