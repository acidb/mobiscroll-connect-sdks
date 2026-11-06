<?php

declare(strict_types=1);

namespace Mobiscroll\Connect;

/**
 * An event from any calendar provider.
 *
 * `changeType` ('created', 'updated' or 'deleted') is set only on events in a webhook delivery. Deleted
 * Microsoft, Apple and CalDAV events arrive as placeholders without `original`; rely only on `id`, `provider`,
 * `calendarId` and `changeType` for them.
 *
 * @property-read array<string, mixed> $attendees
 * @property-read array<string, mixed> $custom
 */
class CalendarEvent
{
    /**
     * @param array<string, mixed> $original
     * @param array<string, mixed>|null $attendees
     * @param array<string, mixed>|null $custom
     * @param string|null $lastModified ISO 8601 timestamp of the last modification
     * @param string|null $changeType 'created', 'updated' or 'deleted'; set only in webhook deliveries
     */
    public function __construct(
        public readonly string $provider,
        public readonly string $id,
        public readonly string $calendarId,
        public readonly string $title,
        public readonly \DateTime $start,
        public readonly \DateTime $end,
        public readonly bool $allDay,
        public readonly ?string $recurringEventId = null,
        public readonly ?string $color = null,
        public readonly ?string $location = null,
        public readonly ?array $attendees = null,
        public readonly ?array $custom = null,
        public readonly ?string $conference = null,
        public readonly ?string $availability = null,
        public readonly ?string $privacy = null,
        public readonly ?string $status = null,
        public readonly ?string $link = null,
        public readonly array $original = [],
        public readonly ?string $description = null,
        public readonly ?ConferenceData $conferenceData = null,
        public readonly ?string $lastModified = null,
        public readonly ?string $changeType = null,
    ) {
    }

    /**
     * `$provider` and `$calendarId` are fallbacks for an event that omits them, as deleted placeholders in
     * webhook deliveries sent before Connect 1.6.1 do; with either fallback a missing `id` becomes ''.
     *
     * @param array<string, mixed> $data
     * @throws \InvalidArgumentException When `provider`, `id` or `calendarId` is missing and has no fallback
     */
    public static function fromArray(array $data, ?string $provider = null, ?string $calendarId = null): self
    {
        if ($provider !== null || $calendarId !== null) {
            foreach (['provider' => $provider, 'calendarId' => $calendarId, 'id' => ''] as $key => $fallback) {
                if ($fallback !== null) {
                    $data[$key] ??= $fallback;
                }
            }
        }
        $conferenceData = $data['conferenceData'] ?? null;
        $changeType = $data['changeType'] ?? null;

        return new self(
            $data['provider'] ?? throw new \InvalidArgumentException('provider is required'),
            $data['id'] ?? throw new \InvalidArgumentException('id is required'),
            $data['calendarId'] ?? throw new \InvalidArgumentException('calendarId is required'),
            $data['title'] ?? '',
            new \DateTime($data['start'] ?? 'now'),
            new \DateTime($data['end'] ?? 'now'),
            $data['allDay'] ?? false,
            $data['recurringEventId'] ?? null,
            $data['color'] ?? null,
            $data['location'] ?? null,
            $data['attendees'] ?? null,
            $data['custom'] ?? null,
            $data['conference'] ?? null,
            $data['availability'] ?? null,
            $data['privacy'] ?? null,
            $data['status'] ?? null,
            $data['link'] ?? null,
            $data['original'] ?? [],
            $data['description'] ?? null,
            is_array($conferenceData) ? ConferenceData::fromArray($conferenceData) : null,
            $data['lastModified'] ?? null,
            is_string($changeType) ? $changeType : null,
        );
    }
}
