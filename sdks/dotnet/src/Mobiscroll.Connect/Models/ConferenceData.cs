using System.Collections.Generic;
using System.Text.Json;

namespace Mobiscroll.Connect.Models;

/// <summary>
/// The conference of a <see cref="CalendarEvent"/>, with the same fields on every provider.
/// </summary>
public sealed class ConferenceData
{
    /// <summary>The conference system: "hangoutsMeet", "google-meet", "teams" or "caldav".</summary>
    public string? Provider { get; set; }

    /// <summary>The join URL, the same value as <see cref="CalendarEvent.Conference"/>.</summary>
    public string? Url { get; set; }

    /// <summary>Whether a meeting link was requested on create or update.</summary>
    public bool? AutoGenerate { get; set; }

    /// <summary>
    /// The conference data exactly as the calendar provider returns it: Google's <c>conferenceData</c>,
    /// Microsoft's <c>onlineMeeting</c>. <c>null</c> for Apple and CalDAV.
    /// </summary>
    public Dictionary<string, JsonElement>? Original { get; set; }
}
