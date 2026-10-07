using System;
using System.Collections.Generic;
using System.Globalization;
using System.Text;
using Mobiscroll.Connect.Exceptions;
using NSec.Cryptography;

namespace Mobiscroll.Connect;

/// <summary>Options for <see cref="WebhookVerifier.VerifyWebhookSignature(ReadOnlyMemory{byte}, IEnumerable{KeyValuePair{string, string}}, IEnumerable{string}, WebhookVerificationOptions?)"/>.</summary>
public sealed class WebhookVerificationOptions
{
    /// <summary>Maximum age, in seconds, of <c>webhook-timestamp</c> in either direction. Default 300.</summary>
    public int ToleranceSeconds { get; init; } = WebhookVerifier.DefaultToleranceSeconds;

    /// <summary>Current Unix time in seconds; defaults to the system clock.</summary>
    public long? Now { get; init; }
}

/// <summary>
/// Verifies the Standard Webhooks <c>v1a</c> (Ed25519) signature of Mobiscroll Connect webhook
/// deliveries against public keys you supply, without fetching anything.
/// </summary>
/// <remarks>
/// Use it with a pinned key in handlers that cannot make outbound requests. Otherwise prefer
/// <see cref="Resources.Webhooks"/>' <c>VerifyWebhookAsync</c>, which fetches and refreshes
/// the keys for you.
/// </remarks>
public static class WebhookVerifier
{
    /// <summary>Default maximum age, in seconds, of a delivery's <c>webhook-timestamp</c>.</summary>
    public const int DefaultToleranceSeconds = 300;

    /// <summary>Path, on the API origin, of the endpoint that publishes the webhook public keys.</summary>
    public const string KeysPath = "/.well-known/webhook-keys";

    internal const string PublicKeyPrefix = "whpk_";

    private const string SignatureVersion = "v1a";
    private const int PublicKeyLength = 32;
    private const int SignatureLength = 64;

    /// <summary>Verifies a delivery whose raw body is given as bytes.</summary>
    /// <param name="payload">The raw request body, exactly as received.</param>
    /// <param name="headers">The request headers (any casing); must include <c>webhook-id</c>, <c>webhook-timestamp</c> and <c>webhook-signature</c>.</param>
    /// <param name="publicKeys"><c>whpk_</c> public keys; the delivery is genuine if any signature verifies against any key.</param>
    /// <param name="options">Timestamp tolerance (default 300 s) and the clock to check it against.</param>
    /// <exception cref="WebhookVerificationException">The delivery is not genuine or cannot be checked.</exception>
    public static void VerifyWebhookSignature(
        ReadOnlyMemory<byte> payload,
        IEnumerable<KeyValuePair<string, string>> headers,
        IEnumerable<string> publicKeys,
        WebhookVerificationOptions? options = null)
        => Verify(payload.Span, WebhookSignatureHeaders.From(headers), publicKeys, options);

    /// <summary>Verifies a delivery whose raw body is given as a string.</summary>
    /// <param name="payload">The raw request body, exactly as received; not re-serialized JSON.</param>
    /// <param name="headers">The request headers (any casing); must include <c>webhook-id</c>, <c>webhook-timestamp</c> and <c>webhook-signature</c>.</param>
    /// <param name="publicKeys"><c>whpk_</c> public keys; the delivery is genuine if any signature verifies against any key.</param>
    /// <param name="options">Timestamp tolerance (default 300 s) and the clock to check it against.</param>
    /// <exception cref="WebhookVerificationException">The delivery is not genuine or cannot be checked.</exception>
    public static void VerifyWebhookSignature(
        string payload,
        IEnumerable<KeyValuePair<string, string>> headers,
        IEnumerable<string> publicKeys,
        WebhookVerificationOptions? options = null)
        => Verify(EncodePayload(payload), WebhookSignatureHeaders.From(headers), publicKeys, options);

    /// <summary>
    /// Verifies a delivery whose headers are multi-valued, such as <see cref="System.Net.Http.Headers.HttpHeaders"/>
    /// or ASP.NET Core's <c>IHeaderDictionary</c>. The first value of each header is used.
    /// </summary>
    /// <param name="payload">The raw request body, exactly as received.</param>
    /// <param name="headers">The request headers (any casing); must include <c>webhook-id</c>, <c>webhook-timestamp</c> and <c>webhook-signature</c>.</param>
    /// <param name="publicKeys"><c>whpk_</c> public keys; the delivery is genuine if any signature verifies against any key.</param>
    /// <param name="options">Timestamp tolerance (default 300 s) and the clock to check it against.</param>
    /// <exception cref="WebhookVerificationException">The delivery is not genuine or cannot be checked.</exception>
    public static void VerifyWebhookSignature<TValues>(
        ReadOnlyMemory<byte> payload,
        IEnumerable<KeyValuePair<string, TValues>> headers,
        IEnumerable<string> publicKeys,
        WebhookVerificationOptions? options = null)
        where TValues : IEnumerable<string?>
        => Verify(payload.Span, WebhookSignatureHeaders.From(headers), publicKeys, options);

    /// <summary>
    /// Verifies a delivery whose raw body is given as a string and whose headers are multi-valued,
    /// such as <see cref="System.Net.Http.Headers.HttpHeaders"/> or ASP.NET Core's <c>IHeaderDictionary</c>.
    /// </summary>
    /// <param name="payload">The raw request body, exactly as received; not re-serialized JSON.</param>
    /// <param name="headers">The request headers (any casing); must include <c>webhook-id</c>, <c>webhook-timestamp</c> and <c>webhook-signature</c>.</param>
    /// <param name="publicKeys"><c>whpk_</c> public keys; the delivery is genuine if any signature verifies against any key.</param>
    /// <param name="options">Timestamp tolerance (default 300 s) and the clock to check it against.</param>
    /// <exception cref="WebhookVerificationException">The delivery is not genuine or cannot be checked.</exception>
    public static void VerifyWebhookSignature<TValues>(
        string payload,
        IEnumerable<KeyValuePair<string, TValues>> headers,
        IEnumerable<string> publicKeys,
        WebhookVerificationOptions? options = null)
        where TValues : IEnumerable<string?>
        => Verify(EncodePayload(payload), WebhookSignatureHeaders.From(headers), publicKeys, options);

    internal static byte[] EncodePayload(string payload)
    {
        if (payload is null)
        {
            throw new ArgumentNullException(nameof(payload));
        }
        return Encoding.UTF8.GetBytes(payload);
    }

    internal static void Verify(
        ReadOnlySpan<byte> body,
        WebhookSignatureHeaders headers,
        IEnumerable<string> publicKeys,
        WebhookVerificationOptions? options)
    {
        if (publicKeys is null)
        {
            throw new ArgumentNullException(nameof(publicKeys));
        }

        var id = headers.Id;
        var timestamp = headers.Timestamp;
        var signatureHeader = headers.Signature;
        if (string.IsNullOrEmpty(id) || string.IsNullOrEmpty(timestamp) || string.IsNullOrEmpty(signatureHeader))
        {
            throw new WebhookVerificationException(
                "Missing webhook-id, webhook-timestamp or webhook-signature header",
                WebhookVerificationReason.MissingHeaders);
        }

        if (!IsAsciiDigits(timestamp))
        {
            throw new WebhookVerificationException(
                "Invalid webhook-timestamp header",
                WebhookVerificationReason.InvalidTimestamp);
        }
        var tolerance = options?.ToleranceSeconds ?? DefaultToleranceSeconds;
        var now = options?.Now ?? DateTimeOffset.UtcNow.ToUnixTimeSeconds();
        if (!decimal.TryParse(timestamp, NumberStyles.None, CultureInfo.InvariantCulture, out var sentAt)
            || Math.Abs(now - sentAt) > tolerance)
        {
            throw new WebhookVerificationException(
                "Webhook timestamp is outside the allowed tolerance",
                WebhookVerificationReason.TimestampOutOfTolerance);
        }

        var keys = new List<PublicKey>();
        foreach (var value in publicKeys)
        {
            var key = ParsePublicKey(value);
            if (key is not null)
            {
                keys.Add(key);
            }
        }
        if (keys.Count == 0)
        {
            throw new WebhookVerificationException(
                "No valid webhook public keys available",
                WebhookVerificationReason.NoPublicKeys);
        }

        var prefix = Encoding.UTF8.GetBytes($"{id}.{timestamp}.");
        var signedContent = new byte[prefix.Length + body.Length];
        prefix.CopyTo(signedContent, 0);
        body.CopyTo(signedContent.AsSpan(prefix.Length));

        Span<byte> signature = stackalloc byte[SignatureLength];
        foreach (var entry in signatureHeader.Split(' '))
        {
            var separator = entry.IndexOf(',');
            if (separator == -1 || !entry.AsSpan(0, separator).SequenceEqual(SignatureVersion))
            {
                continue;
            }
            if (!Convert.TryFromBase64String(entry.Substring(separator + 1), signature, out var written)
                || written != SignatureLength)
            {
                continue;
            }
            foreach (var key in keys)
            {
                if (SignatureAlgorithm.Ed25519.Verify(key, signedContent, signature))
                {
                    return;
                }
            }
        }

        throw new WebhookVerificationException(
            "No webhook signature matched",
            WebhookVerificationReason.NoMatchingSignature);
    }

    private static bool IsAsciiDigits(string value)
    {
        foreach (var c in value)
        {
            if (c is < '0' or > '9')
            {
                return false;
            }
        }
        return value.Length > 0;
    }

    private static PublicKey? ParsePublicKey(string? value)
    {
        if (value is null)
        {
            return null;
        }
        var encoded = value.Trim();
        if (encoded.StartsWith(PublicKeyPrefix, StringComparison.Ordinal))
        {
            encoded = encoded.Substring(PublicKeyPrefix.Length);
        }

        Span<byte> raw = stackalloc byte[PublicKeyLength];
        if (!Convert.TryFromBase64String(encoded, raw, out var written) || written != PublicKeyLength)
        {
            return null;
        }
        return PublicKey.TryImport(SignatureAlgorithm.Ed25519, raw, KeyBlobFormat.RawPublicKey, out var key)
            ? key
            : null;
    }
}

/// <summary>The three Standard Webhooks headers, looked up case-insensitively; first value wins.</summary>
internal readonly record struct WebhookSignatureHeaders(string? Id, string? Timestamp, string? Signature)
{
    private const string IdHeader = "webhook-id";
    private const string TimestampHeader = "webhook-timestamp";
    private const string SignatureHeader = "webhook-signature";

    public static WebhookSignatureHeaders From(IEnumerable<KeyValuePair<string, string>> headers)
    {
        if (headers is null)
        {
            throw new ArgumentNullException(nameof(headers));
        }
        string? id = null, timestamp = null, signature = null;
        foreach (var header in headers)
        {
            Assign(header.Key, header.Value, ref id, ref timestamp, ref signature);
        }
        return new WebhookSignatureHeaders(id, timestamp, signature);
    }

    public static WebhookSignatureHeaders From<TValues>(IEnumerable<KeyValuePair<string, TValues>> headers)
        where TValues : IEnumerable<string?>
    {
        if (headers is null)
        {
            throw new ArgumentNullException(nameof(headers));
        }
        string? id = null, timestamp = null, signature = null;
        foreach (var header in headers)
        {
            if (header.Value is null)
            {
                continue;
            }
            foreach (var value in header.Value)
            {
                Assign(header.Key, value, ref id, ref timestamp, ref signature);
                break;
            }
        }
        return new WebhookSignatureHeaders(id, timestamp, signature);
    }

    private static void Assign(string name, string? value, ref string? id, ref string? timestamp, ref string? signature)
    {
        if (string.Equals(name, IdHeader, StringComparison.OrdinalIgnoreCase))
        {
            id ??= value;
        }
        else if (string.Equals(name, TimestampHeader, StringComparison.OrdinalIgnoreCase))
        {
            timestamp ??= value;
        }
        else if (string.Equals(name, SignatureHeader, StringComparison.OrdinalIgnoreCase))
        {
            signature ??= value;
        }
    }
}
