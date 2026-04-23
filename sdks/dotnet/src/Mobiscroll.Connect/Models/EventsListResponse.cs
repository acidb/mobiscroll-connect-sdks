using System.Collections.Generic;

namespace Mobiscroll.Connect.Models;

public sealed class EventsListResponse
{
    public List<CalendarEvent> Events { get; set; } = new();
    public int? PageSize { get; set; }
    public string? NextPageToken { get; set; }
}
