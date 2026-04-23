using System.Collections.Generic;

namespace Mobiscroll.Connect.Models;

public sealed class RecurrenceRule
{
    /// <summary>"DAILY" | "WEEKLY" | "MONTHLY" | "YEARLY"</summary>
    public string Frequency { get; set; } = "DAILY";

    public int? Interval { get; set; }
    public int? Count { get; set; }
    public string? Until { get; set; }
    public List<string>? ByDay { get; set; }
    public List<int>? ByMonthDay { get; set; }
    public List<int>? ByMonth { get; set; }
}
