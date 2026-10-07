namespace Mobiscroll.Connect;

public sealed class MobiscrollConnectConfig
{
    public string ClientId { get; set; } = string.Empty;
    public string ClientSecret { get; set; } = string.Empty;
    public string RedirectUri { get; set; } = string.Empty;

    /// <summary>
    /// Pinned <c>whpk_</c> webhook public key. Used by
    /// <see cref="Resources.Webhooks"/>' <c>VerifyWebhookAsync</c> only when the keys endpoint
    /// cannot be reached; it stops verifying once Mobiscroll retires that key.
    /// </summary>
    public string? WebhookPublicKey { get; set; }
}
