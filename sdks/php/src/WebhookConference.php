<?php

declare(strict_types=1);

namespace Mobiscroll\Connect;

/**
 * Conference details on a webhook delivery event. Deliveries carry an object here, while
 * `events()->list()` returns the join URL as a string in `CalendarEvent::$conference`.
 */
class WebhookConference
{
    /**
     * @param string $url Conference join URL
     * @param string|null $provider Conference provider as the calendar provider reports it, e.g. 'hangoutsMeet', 'teams'
     * @param string|null $meetingProvider Same value as `provider`
     * @param array<string, mixed>|null $data Provider-specific conference payload
     */
    public function __construct(
        public readonly string $url,
        public readonly ?string $provider = null,
        public readonly ?string $meetingProvider = null,
        public readonly ?array $data = null,
    ) {
    }

    /**
     * Builds the conference from its wire value; `null` when absent or empty.
     */
    public static function fromValue(mixed $value): ?self
    {
        if (is_string($value)) {
            return $value === '' ? null : new self($value);
        }
        if (!is_array($value)) {
            return null;
        }
        $data = $value['data'] ?? null;
        /** @var array<string, mixed>|null $data */
        $data = is_array($data) ? $data : null;

        return new self(
            is_string($value['url'] ?? null) ? $value['url'] : '',
            is_string($value['provider'] ?? null) ? $value['provider'] : null,
            is_string($value['meetingProvider'] ?? null) ? $value['meetingProvider'] : null,
            $data,
        );
    }
}
