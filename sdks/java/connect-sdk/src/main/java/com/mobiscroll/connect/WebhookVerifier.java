package com.mobiscroll.connect;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.mobiscroll.connect.exceptions.WebhookVerificationException;
import com.mobiscroll.connect.exceptions.WebhookVerificationException.Reason;
import com.mobiscroll.connect.models.VerifyWebhookSignatureOptions;

/**
 * Verifies the Standard Webhooks {@code v1a} (Ed25519) signature of a Mobiscroll Connect webhook
 * delivery against public keys you supply, without fetching anything.
 *
 * <p>Use it with a pinned key in handlers that cannot make outbound requests. Otherwise prefer
 * {@code client.webhooks().verifyWebhook(...)}, which fetches and refreshes the keys for you.
 *
 * <pre>
 * WebhookVerifier.verifyWebhookSignature(rawBody, headers,
 *         List.of(System.getenv("MOBISCROLL_WEBHOOK_PUBLIC_KEY")));
 * </pre>
 */
public final class WebhookVerifier {

    private static final String PUBLIC_KEY_PREFIX = "whpk_";
    private static final String SIGNATURE_VERSION = "v1a";
    private static final Pattern TIMESTAMP = Pattern.compile("^\\d+$");
    /** DER prefix of an X.509 SubjectPublicKeyInfo holding a raw 32-byte Ed25519 key. */
    private static final byte[] ED25519_SPKI_PREFIX = {
        0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    };

    private WebhookVerifier() {}

    /** Same as {@link #verifyWebhookSignature(byte[], Map, List, VerifyWebhookSignatureOptions)} with default options. */
    public static void verifyWebhookSignature(byte[] payload, Map<String, ?> headers, List<String> publicKeys) {
        verifyWebhookSignature(payload, headers, publicKeys, null);
    }

    /** Same as {@link #verifyWebhookSignature(byte[], Map, List, VerifyWebhookSignatureOptions)} with default options. */
    public static void verifyWebhookSignature(String payload, Map<String, ?> headers, List<String> publicKeys) {
        verifyWebhookSignature(payload, headers, publicKeys, null);
    }

    /**
     * Same as {@link #verifyWebhookSignature(byte[], Map, List, VerifyWebhookSignatureOptions)} for a body
     * held as a string; it is encoded as UTF-8.
     */
    public static void verifyWebhookSignature(String payload, Map<String, ?> headers, List<String> publicKeys,
            VerifyWebhookSignatureOptions options) {
        verifyWebhookSignature(toBytes(payload), headers, publicKeys, options);
    }

    /**
     * Verifies a delivery against the given public keys.
     *
     * @param payload the raw request body, exactly as received
     * @param headers the request headers, looked up case-insensitively; values may be {@code String}s or
     *                collections of them (the first is used), so both {@code Map<String, String>} and
     *                {@code Map<String, List<String>>} work
     * @param publicKeys {@code whpk_} public keys; the delivery is genuine if any signature verifies against
     *                   any key. Malformed keys are skipped.
     * @param options timestamp tolerance (default 300 s) and the clock to check it against; may be {@code null}
     * @throws WebhookVerificationException when the delivery is not genuine or cannot be checked
     */
    public static void verifyWebhookSignature(byte[] payload, Map<String, ?> headers, List<String> publicKeys,
            VerifyWebhookSignatureOptions options) {
        if (payload == null) {
            throw new WebhookVerificationException("Pass the raw request body", Reason.INVALID_PAYLOAD);
        }
        String id = readHeader(headers, "webhook-id");
        String timestamp = readHeader(headers, "webhook-timestamp");
        String signatureHeader = readHeader(headers, "webhook-signature");
        if (isEmpty(id) || isEmpty(timestamp) || isEmpty(signatureHeader)) {
            throw new WebhookVerificationException(
                    "Missing webhook-id, webhook-timestamp or webhook-signature header", Reason.MISSING_HEADERS);
        }

        if (!TIMESTAMP.matcher(timestamp).matches()) {
            throw new WebhookVerificationException("Invalid webhook-timestamp header", Reason.INVALID_TIMESTAMP);
        }
        long tolerance = options != null ? options.getToleranceSeconds()
                : VerifyWebhookSignatureOptions.DEFAULT_TOLERANCE_SECONDS;
        long now = options != null && options.getNow() != null
                ? options.getNow()
                : System.currentTimeMillis() / 1000;
        if (!withinTolerance(timestamp, now, tolerance)) {
            throw new WebhookVerificationException(
                    "Webhook timestamp is outside the allowed tolerance", Reason.TIMESTAMP_OUT_OF_TOLERANCE);
        }

        List<PublicKey> keys = new ArrayList<>();
        if (publicKeys != null) {
            for (String value : publicKeys) {
                PublicKey key = parsePublicKey(value);
                if (key != null) {
                    keys.add(key);
                }
            }
        }
        if (keys.isEmpty()) {
            throw new WebhookVerificationException("No valid webhook public keys available", Reason.NO_PUBLIC_KEYS);
        }

        byte[] prefix = (id + "." + timestamp + ".").getBytes(StandardCharsets.UTF_8);
        byte[] signedContent = new byte[prefix.length + payload.length];
        System.arraycopy(prefix, 0, signedContent, 0, prefix.length);
        System.arraycopy(payload, 0, signedContent, prefix.length, payload.length);

        for (String entry : signatureHeader.split(" ")) {
            int separator = entry.indexOf(',');
            if (separator == -1 || !SIGNATURE_VERSION.equals(entry.substring(0, separator))) {
                continue;
            }
            byte[] signature = decodeBase64(entry.substring(separator + 1));
            if (signature == null || signature.length != 64) {
                continue;
            }
            for (PublicKey key : keys) {
                if (verifies(key, signedContent, signature)) {
                    return;
                }
            }
        }
        throw new WebhookVerificationException("No webhook signature matched", Reason.NO_MATCHING_SIGNATURE);
    }

    private static byte[] toBytes(String payload) {
        if (payload == null) {
            throw new WebhookVerificationException("Pass the raw request body", Reason.INVALID_PAYLOAD);
        }
        return payload.getBytes(StandardCharsets.UTF_8);
    }

    private static String readHeader(Map<String, ?> headers, String name) {
        if (headers == null) {
            return null;
        }
        for (Map.Entry<String, ?> header : headers.entrySet()) {
            if (header.getKey() == null || !header.getKey().equalsIgnoreCase(name)) {
                continue;
            }
            Object value = header.getValue();
            if (value instanceof Collection<?> values) {
                value = values.isEmpty() ? null : values.iterator().next();
            } else if (value instanceof String[] values) {
                value = values.length == 0 ? null : values[0];
            }
            return value instanceof String text ? text : null;
        }
        return null;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    private static boolean withinTolerance(String timestamp, long now, long tolerance) {
        long seconds;
        try {
            seconds = Long.parseLong(timestamp);
        } catch (NumberFormatException tooLarge) {
            return false;
        }
        return Math.abs(now - seconds) <= tolerance;
    }

    private static PublicKey parsePublicKey(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith(PUBLIC_KEY_PREFIX)) {
            trimmed = trimmed.substring(PUBLIC_KEY_PREFIX.length());
        }
        byte[] raw = decodeBase64(trimmed);
        if (raw == null || raw.length != 32) {
            return null;
        }
        byte[] spki = new byte[ED25519_SPKI_PREFIX.length + raw.length];
        System.arraycopy(ED25519_SPKI_PREFIX, 0, spki, 0, ED25519_SPKI_PREFIX.length);
        System.arraycopy(raw, 0, spki, ED25519_SPKI_PREFIX.length, raw.length);
        try {
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(spki));
        } catch (GeneralSecurityException | RuntimeException e) {
            return null;
        }
    }

    private static byte[] decodeBase64(String value) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean verifies(PublicKey key, byte[] signedContent, byte[] signature) {
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key);
            verifier.update(signedContent);
            return verifier.verify(signature);
        } catch (GeneralSecurityException | RuntimeException e) {
            return false;
        }
    }
}
