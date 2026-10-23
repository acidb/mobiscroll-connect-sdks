using System.Text.Json;

namespace Mobiscroll.Connect.Models;

public sealed class Calendar
{
    public Provider Provider { get; set; }
    public string Id { get; set; } = string.Empty;
    public string Title { get; set; } = string.Empty;
    public string TimeZone { get; set; } = string.Empty;
    public string Color { get; set; } = string.Empty;
    public string Description { get; set; } = string.Empty;

    /// <summary>The connected account this calendar belongs to.</summary>
    public string Account { get; set; } = string.Empty;

    /// <summary><c>true</c> when the authenticated connection cannot write to this calendar.</summary>
    public bool ReadOnly { get; set; }

    /// <summary>
    /// Raw provider-specific payload. Kept as JsonElement so the consumer can inspect
    /// fields that are not modeled by the SDK.
    /// </summary>
    public JsonElement? Original { get; set; }
}
