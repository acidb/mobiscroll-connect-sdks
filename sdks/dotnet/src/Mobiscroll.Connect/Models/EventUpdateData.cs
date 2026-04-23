namespace Mobiscroll.Connect.Models;

public sealed class EventUpdateData : EventCreateData
{
    public string EventId { get; set; } = string.Empty;
    public string? RecurringEventId { get; set; }

    /// <summary>"this" | "following" | "all"</summary>
    public string? UpdateMode { get; set; }
}
