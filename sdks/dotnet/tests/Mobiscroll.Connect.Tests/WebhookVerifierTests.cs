using System;
using System.Collections.Generic;
using System.Linq;
using System.Net.Http;
using System.Text;
using Microsoft.Extensions.Primitives;
using Mobiscroll.Connect.Exceptions;
using Mobiscroll.Connect.Tests.TestHelpers;

namespace Mobiscroll.Connect.Tests;

public class WebhookVerifierTests
{
    public static IEnumerable<object[]> Vectors => WebhookVectors.Cases.Select(c => new object[] { c.Name });

    private static WebhookVerificationOptions At(long now) => new() { Now = now };

    [Theory]
    [MemberData(nameof(Vectors))]
    public void VerifyWebhookSignature_MatchesServerVectors(string name)
    {
        var vector = WebhookVectors.ByName(name);
        var exception = Record.Exception(() =>
            WebhookVerifier.VerifyWebhookSignature(vector.Body, vector.Headers, vector.PublicKeys, At(vector.Now)));

        if (vector.Valid)
        {
            Assert.Null(exception);
        }
        else
        {
            Assert.IsType<WebhookVerificationException>(exception);
        }
    }

    [Fact]
    public void VerifyWebhookSignature_AcceptsBytesAndMultiValuedHeadersInAnyCasing()
    {
        var vector = WebhookVectors.ByName("valid single signature");
        var headers = vector.Headers.ToDictionary(h => h.Key.ToUpperInvariant(), h => new[] { h.Value, "ignored" });

        WebhookVerifier.VerifyWebhookSignature(Encoding.UTF8.GetBytes(vector.Body), headers, vector.PublicKeys, At(vector.Now));
    }

    [Fact]
    public void VerifyWebhookSignature_AcceptsHttpHeaders()
    {
        var vector = WebhookVectors.ByName("valid single signature");
        using var request = new HttpRequestMessage();
        foreach (var header in vector.Headers)
        {
            request.Headers.TryAddWithoutValidation(header.Key, header.Value);
        }

        WebhookVerifier.VerifyWebhookSignature(vector.Body, request.Headers, vector.PublicKeys, At(vector.Now));
    }

    [Fact]
    public void VerifyWebhookSignature_AcceptsStringValuesHeadersLikeIHeaderDictionary()
    {
        var vector = WebhookVectors.ByName("valid single signature");
        var headers = new Dictionary<string, StringValues>(StringComparer.OrdinalIgnoreCase);
        foreach (var header in vector.Headers)
        {
            headers["X-" + header.Key] = "decoy";
            headers[header.Key.ToUpperInvariant()] = new StringValues(header.Value);
        }

        WebhookVerifier.VerifyWebhookSignature(vector.Body, headers, vector.PublicKeys, At(vector.Now));
    }

    [Fact]
    public void VerifyWebhookSignature_ReportsWhyVerificationFailed()
    {
        var stale = WebhookVectors.ByName("timestamp 301 s old, stale");

        var ex = Assert.Throws<WebhookVerificationException>(() =>
            WebhookVerifier.VerifyWebhookSignature(stale.Body, stale.Headers, stale.PublicKeys, At(stale.Now)));

        Assert.Equal(WebhookVerificationReason.TimestampOutOfTolerance, ex.Reason);
        Assert.Equal("WEBHOOK_VERIFICATION_ERROR", ex.CodeString);
        Assert.IsAssignableFrom<MobiscrollConnectException>(ex);
    }

    [Theory]
    [InlineData("missing webhook-id", WebhookVerificationReason.MissingHeaders)]
    [InlineData("non-numeric timestamp", WebhookVerificationReason.InvalidTimestamp)]
    [InlineData("timestamp 301 s in the future", WebhookVerificationReason.TimestampOutOfTolerance)]
    [InlineData("no public keys", WebhookVerificationReason.NoPublicKeys)]
    [InlineData("tampered body", WebhookVerificationReason.NoMatchingSignature)]
    [InlineData("malformed base64 signature", WebhookVerificationReason.NoMatchingSignature)]
    [InlineData("only a v1 HMAC entry", WebhookVerificationReason.NoMatchingSignature)]
    public void VerifyWebhookSignature_ReportsTheReason(string name, WebhookVerificationReason expected)
    {
        var vector = WebhookVectors.ByName(name);

        var ex = Assert.Throws<WebhookVerificationException>(() =>
            WebhookVerifier.VerifyWebhookSignature(vector.Body, vector.Headers, vector.PublicKeys, At(vector.Now)));

        Assert.Equal(expected, ex.Reason);
    }

    [Theory]
    [InlineData("１７９００００００００")]
    [InlineData("1790000000\n")]
    [InlineData("-1790000000")]
    public void VerifyWebhookSignature_AcceptsOnlyAsciiDigitTimestamps(string timestamp)
    {
        var vector = WebhookVectors.ByName("valid single signature");
        var headers = new Dictionary<string, string>(vector.Headers) { ["webhook-timestamp"] = timestamp };

        var ex = Assert.Throws<WebhookVerificationException>(() =>
            WebhookVerifier.VerifyWebhookSignature(vector.Body, headers, vector.PublicKeys, At(vector.Now)));

        Assert.Equal(WebhookVerificationReason.InvalidTimestamp, ex.Reason);
    }

    [Fact]
    public void VerifyWebhookSignature_TreatsAnOverflowingTimestampAsOutOfTolerance()
    {
        var vector = WebhookVectors.ByName("valid single signature");
        var headers = new Dictionary<string, string>(vector.Headers) { ["webhook-timestamp"] = new string('9', 40) };

        var ex = Assert.Throws<WebhookVerificationException>(() =>
            WebhookVerifier.VerifyWebhookSignature(vector.Body, headers, vector.PublicKeys, At(vector.Now)));

        Assert.Equal(WebhookVerificationReason.TimestampOutOfTolerance, ex.Reason);
    }

    [Fact]
    public void VerifyWebhookSignature_HonoursACustomTolerance()
    {
        var stale = WebhookVectors.ByName("timestamp 301 s old, stale");

        WebhookVerifier.VerifyWebhookSignature(
            stale.Body,
            stale.Headers,
            stale.PublicKeys,
            new WebhookVerificationOptions { Now = stale.Now, ToleranceSeconds = 301 });
    }

    [Fact]
    public void VerifyWebhookSignature_UsesTheSystemClockByDefault()
    {
        var vector = WebhookVectors.ByName("valid single signature");

        var ex = Assert.Throws<WebhookVerificationException>(() =>
            WebhookVerifier.VerifyWebhookSignature(vector.Body, vector.Headers, vector.PublicKeys));

        Assert.Equal(WebhookVerificationReason.TimestampOutOfTolerance, ex.Reason);
    }
}
