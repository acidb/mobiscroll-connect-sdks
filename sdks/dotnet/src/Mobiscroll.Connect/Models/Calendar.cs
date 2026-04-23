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

    /// <summary>
    /// Raw provider-specific payload. Kept as JsonElement so the consumer can inspect
    /// fields that are not modeled by the SDK.
    /// </summary>
    public JsonElement? Original { get; set; }
}
