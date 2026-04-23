using System.Text.Json;

namespace Mobiscroll.Connect.Exceptions;

public sealed class ValidationException : MobiscrollConnectException
{
    /// <summary>Raw validation detail payload returned by the API, if any.</summary>
    public JsonElement? Details { get; }

    public ValidationException(string message, JsonElement? details = null)
        : base(message, "VALIDATION_ERROR")
    {
        Details = details;
    }
}
