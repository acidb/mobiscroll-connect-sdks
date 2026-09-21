namespace Mobiscroll.Connect.Models;

public sealed class WebhookUnsubscribeResponse
{
    public bool Success { get; set; }

    /// <summary>
    /// Explanatory message, e.g. when the provider-side subscription had already expired.
    /// <see cref="Success"/> is still <c>true</c> in that case — treat the response as final
    /// regardless.
    /// </summary>
    public string? Message { get; set; }
}
