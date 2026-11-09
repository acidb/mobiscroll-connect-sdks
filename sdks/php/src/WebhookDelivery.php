<?php

declare(strict_types=1);

namespace Mobiscroll\Connect;

/**
 * A verified webhook delivery sent by Mobiscroll Connect to the project's webhook URL.
 */
class WebhookDelivery
{
    /**
     * @param string $provider 'google', 'microsoft', 'apple' or 'caldav'
     * @param WebhookEvent[] $events
     * @param string|null $changeType 'created', 'updated', 'deleted' or 'mixed'
     * @param string $timestamp ISO 8601 timestamp of when Connect processed the change
     */
    public function __construct(
        public readonly string $provider,
        public readonly string $userId,
        public readonly string $calendarId,
        public readonly array $events,
        public readonly ?string $changeType,
        public readonly string $timestamp,
        public readonly WebhookDeliveryMetadata $metadata,
    ) {
    }

    /**
     * @param array<string, mixed> $data
     */
    public static function fromArray(array $data): self
    {
        $provider = self::stringOrNull($data['provider'] ?? null) ?? '';
        $calendarId = self::stringOrNull($data['calendarId'] ?? null) ?? '';
        $events = [];
        foreach (is_array($data['events'] ?? null) ? $data['events'] : [] as $event) {
            if (is_array($event)) {
                /** @var array<string, mixed> $event */
                $events[] = WebhookEvent::fromArray($event, $provider, $calendarId);
            }
        }
        /** @var array<string, mixed> $metadata */
        $metadata = is_array($data['metadata'] ?? null) ? $data['metadata'] : [];

        return new self(
            $provider,
            self::stringOrNull($data['userId'] ?? null) ?? '',
            $calendarId,
            $events,
            self::stringOrNull($data['changeType'] ?? null),
            self::stringOrNull($data['timestamp'] ?? null) ?? '',
            WebhookDeliveryMetadata::fromArray($metadata, count($events)),
        );
    }

    private static function stringOrNull(mixed $value): ?string
    {
        return is_string($value) ? $value : null;
    }
}
