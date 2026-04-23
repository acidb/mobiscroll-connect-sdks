using System;
using System.Collections.Generic;

namespace Mobiscroll.Connect.Models;

public sealed class EventListParams
{
    public int? PageSize { get; set; }
    public DateTime? Start { get; set; }
    public DateTime? End { get; set; }

    /// <summary>
    /// Calendar IDs grouped by provider wire string, e.g. { "google": ["primary"], "microsoft": ["..."] }.
    /// Serialized as JSON inside a single <c>calendarIds</c> query parameter to match Node/PHP wire format.
    /// </summary>
    public Dictionary<string, List<string>>? CalendarIds { get; set; }

    public string? NextPageToken { get; set; }
    public bool? SingleEvents { get; set; }
}
