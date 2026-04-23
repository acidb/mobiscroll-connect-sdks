namespace Mobiscroll.Connect.Models;

public sealed class EventDeleteParams
{
    public Provider Provider { get; set; }
    public string CalendarId { get; set; } = string.Empty;
    public string EventId { get; set; } = string.Empty;
    public string? RecurringEventId { get; set; }

    /// <summary>"this" | "following" | "all"</summary>
    public string? DeleteMode { get; set; }
}
