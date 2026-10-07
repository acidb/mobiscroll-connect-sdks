namespace Mobiscroll.Connect.Exceptions;

/// <summary>Why a webhook delivery failed verification.</summary>
public enum WebhookVerificationReason
{
    /// <summary><c>webhook-id</c>, <c>webhook-timestamp</c> or <c>webhook-signature</c> is missing or empty.</summary>
    MissingHeaders,

    /// <summary><c>webhook-timestamp</c> is not a whole number of Unix seconds.</summary>
    InvalidTimestamp,

    /// <summary><c>webhook-timestamp</c> is further from the current time than the tolerance allows.</summary>
    TimestampOutOfTolerance,

    /// <summary>No valid <c>whpk_</c> public key was available to verify against.</summary>
    NoPublicKeys,

    /// <summary>No <c>v1a</c> signature verified against any of the public keys.</summary>
    NoMatchingSignature,

    /// <summary>The body is not the raw delivery, or is not valid delivery JSON.</summary>
    InvalidPayload,
}

/// <summary>
/// Thrown when a webhook delivery fails verification. Respond with a 4xx and do not process it.
/// </summary>
public sealed class WebhookVerificationException : MobiscrollConnectException
{
    public WebhookVerificationException(string message, WebhookVerificationReason reason)
        : base(message, "WEBHOOK_VERIFICATION_ERROR")
    {
        Reason = reason;
    }

    /// <summary>Why the delivery was rejected.</summary>
    public WebhookVerificationReason Reason { get; }
}
