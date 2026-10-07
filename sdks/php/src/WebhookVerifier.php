<?php

declare(strict_types=1);

namespace Mobiscroll\Connect;

use Mobiscroll\Connect\Exceptions\WebhookVerificationError;
use Psr\Http\Message\MessageInterface;

/**
 * Standard Webhooks `v1a` (Ed25519) signature check for Mobiscroll Connect deliveries.
 */
final class WebhookVerifier
{
    public const TOLERANCE_SECONDS = 300;
    public const KEYS_PATH = '/.well-known/webhook-keys';
    public const PUBLIC_KEY_PREFIX = 'whpk_';

    private const SIGNATURE_VERSION = 'v1a';

    private function __construct()
    {
    }

    /**
     * Verifies the signature of a Mobiscroll Connect webhook delivery against the given public keys,
     * without fetching anything.
     *
     * Use it with a pinned key in handlers that cannot make outbound requests. Otherwise prefer
     * `$client->webhooks()->verifyWebhook()`, which fetches and refreshes the keys for you.
     *
     * @param string $payload The raw request body, exactly as received
     * @param array<string, string|array<int, string>|null>|MessageInterface $headers The request headers
     *   (any casing); must include `webhook-id`, `webhook-timestamp` and `webhook-signature`
     * @param array<int, string> $publicKeys `whpk_` public keys; the delivery is genuine if any signature
     *   verifies against any key
     * @param int $toleranceSeconds Maximum age of `webhook-timestamp`, in either direction
     * @param int|null $now Current Unix time in seconds; defaults to the system clock
     * @throws WebhookVerificationError When the delivery is not genuine or cannot be checked
     */
    public static function verifyWebhookSignature(
        string $payload,
        array|MessageInterface $headers,
        array $publicKeys,
        int $toleranceSeconds = self::TOLERANCE_SECONDS,
        ?int $now = null,
    ): void {
        $id = self::readHeader($headers, 'webhook-id');
        $timestamp = self::readHeader($headers, 'webhook-timestamp');
        $signatureHeader = self::readHeader($headers, 'webhook-signature');
        if ($id === '' || $timestamp === '' || $signatureHeader === '') {
            throw new WebhookVerificationError(
                'Missing webhook-id, webhook-timestamp or webhook-signature header',
                WebhookVerificationError::MISSING_HEADERS,
            );
        }

        if (preg_match('/\A\d+\z/', $timestamp) !== 1) {
            throw new WebhookVerificationError(
                'Invalid webhook-timestamp header',
                WebhookVerificationError::INVALID_TIMESTAMP,
            );
        }
        if (abs(($now ?? time()) - (float)$timestamp) > $toleranceSeconds) {
            throw new WebhookVerificationError(
                'Webhook timestamp is outside the allowed tolerance',
                WebhookVerificationError::TIMESTAMP_OUT_OF_TOLERANCE,
            );
        }

        $keys = array_values(array_filter(
            array_map(self::parsePublicKey(...), $publicKeys),
            static fn (?string $key): bool => $key !== null,
        ));
        if ($keys === []) {
            throw new WebhookVerificationError(
                'No valid webhook public keys available',
                WebhookVerificationError::NO_PUBLIC_KEYS,
            );
        }

        $signedContent = "{$id}.{$timestamp}." . $payload;
        foreach (explode(' ', $signatureHeader) as $entry) {
            $separator = strpos($entry, ',');
            if ($separator === false || substr($entry, 0, $separator) !== self::SIGNATURE_VERSION) {
                continue;
            }
            $signature = base64_decode(substr($entry, $separator + 1), true);
            if ($signature === false || strlen($signature) !== SODIUM_CRYPTO_SIGN_BYTES) {
                continue;
            }
            foreach ($keys as $key) {
                try {
                    if (sodium_crypto_sign_verify_detached($signature, $signedContent, $key)) {
                        return;
                    }
                } catch (\SodiumException) {
                    continue;
                }
            }
        }

        throw new WebhookVerificationError(
            'No webhook signature matched',
            WebhookVerificationError::NO_MATCHING_SIGNATURE,
        );
    }

    /**
     * @param array<string, string|array<int, string>|null>|MessageInterface $headers
     */
    private static function readHeader(array|MessageInterface $headers, string $name): string
    {
        if ($headers instanceof MessageInterface) {
            return $headers->getHeader($name)[0] ?? '';
        }
        foreach ($headers as $key => $value) {
            if (strtolower((string)$key) === $name) {
                $first = is_array($value) ? reset($value) : $value;
                return is_string($first) ? $first : '';
            }
        }
        return '';
    }

    /**
     * @return non-empty-string|null The raw 32-byte Ed25519 key
     */
    private static function parsePublicKey(mixed $value): ?string
    {
        if (!is_string($value)) {
            return null;
        }
        $value = trim($value);
        if (str_starts_with($value, self::PUBLIC_KEY_PREFIX)) {
            $value = substr($value, strlen(self::PUBLIC_KEY_PREFIX));
        }
        $raw = base64_decode($value, true);
        return $raw !== false && strlen($raw) === SODIUM_CRYPTO_SIGN_PUBLICKEYBYTES ? $raw : null;
    }
}
