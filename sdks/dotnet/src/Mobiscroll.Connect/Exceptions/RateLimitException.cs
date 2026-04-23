namespace Mobiscroll.Connect.Exceptions;

public sealed class RateLimitException : MobiscrollConnectException
{
    /// <summary>Value from the Retry-After header, in seconds, if present.</summary>
    public int? RetryAfter { get; }

    public RateLimitException(string message, int? retryAfter = null)
        : base(message, "RATE_LIMIT_ERROR")
    {
        RetryAfter = retryAfter;
    }
}
