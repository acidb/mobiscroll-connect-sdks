<?php

declare(strict_types=1);

namespace Mobiscroll\Connect\Exceptions;

/**
 * Thrown when a webhook delivery fails verification. Respond with a 4xx and do not process it.
 */
class WebhookVerificationError extends MobiscrollConnectException
{
    public const MISSING_HEADERS = 'missing_headers';
    public const INVALID_TIMESTAMP = 'invalid_timestamp';
    public const TIMESTAMP_OUT_OF_TOLERANCE = 'timestamp_out_of_tolerance';
    public const NO_PUBLIC_KEYS = 'no_public_keys';
    public const NO_MATCHING_SIGNATURE = 'no_matching_signature';
    public const INVALID_PAYLOAD = 'invalid_payload';

    /**
     * @param string $reason One of the class constants, e.g. {@see self::NO_MATCHING_SIGNATURE}
     */
    public function __construct(
        string $message,
        private readonly string $reason,
    ) {
        parent::__construct($message, 'WEBHOOK_VERIFICATION_ERROR');
    }

    /**
     * Why verification failed: `missing_headers`, `invalid_timestamp`, `timestamp_out_of_tolerance`,
     * `no_public_keys`, `no_matching_signature` or `invalid_payload`.
     */
    public function getReason(): string
    {
        return $this->reason;
    }
}
