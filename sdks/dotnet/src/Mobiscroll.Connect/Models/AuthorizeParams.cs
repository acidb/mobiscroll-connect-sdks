namespace Mobiscroll.Connect.Models;

public sealed class AuthorizeParams
{
    public string UserId { get; set; } = string.Empty;

    /// <summary>"read-write" | "free-busy" | "read" — defaults to API-side default when null.</summary>
    public string? Scope { get; set; }

    public string? State { get; set; }

    /// <summary>Comma-separated provider list, e.g. "google,microsoft".</summary>
    public string? Providers { get; set; }

    /// <summary>
    /// Language code for the Connect authorization pages ("en" | "es" | "fr" | "ar").
    /// When null, the Connect UI falls back to the browser Accept-Language header, then English.
    /// </summary>
    public string? Lng { get; set; }
}
