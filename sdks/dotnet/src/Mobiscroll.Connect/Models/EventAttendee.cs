namespace Mobiscroll.Connect.Models;

public sealed class EventAttendee
{
    public string Email { get; set; } = string.Empty;

    /// <summary>"accepted" | "declined" | "tentative" | "none"</summary>
    public string Status { get; set; } = "none";

    public bool? Organizer { get; set; }
}
