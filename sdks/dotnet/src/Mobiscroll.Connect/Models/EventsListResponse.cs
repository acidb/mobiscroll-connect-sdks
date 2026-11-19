using System.Collections.Generic;

namespace Mobiscroll.Connect.Models;

public sealed class EventsListResponse
{
    public List<CalendarEvent> Events { get; set; } = new();
    public int? PageSize { get; set; }
    public string? NextPageToken { get; set; }

    /// <summary>
    /// Messages keyed by provider wire string (e.g. <c>"google"</c>), present when a provider could not be
    /// read, and, with <see cref="EventListParams.EventIds"/>, when no connected account can read a calendar
    /// (that calendar's IDs are then left out of <see cref="Events"/>). <c>null</c> when absent.
    /// </summary>
    public Dictionary<string, string>? Info { get; set; }
}
