namespace Mobiscroll.Connect.Models;

/// <summary>Result of <see cref="Resources.Auth.RevokeAsync"/>.</summary>
public sealed class RevokeResponse
{
    /// <summary>Always <c>true</c> on success, also for an unknown or already revoked token.</summary>
    public bool Success { get; set; }
}
