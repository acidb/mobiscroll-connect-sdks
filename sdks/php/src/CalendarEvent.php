<?php

declare(strict_types=1);

namespace Mobiscroll\Connect;

/**
 * @property-read array<string, mixed> $attendees
 * @property-read array<string, mixed> $custom
 */
class CalendarEvent
{
    /**
     * @param array<string, mixed> $original
     * @param array<string, mixed>|null $attendees
     * @param array<string, mixed>|null $custom
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
    ) {
    }

    /**
     * @param array<string, mixed> $data
     */
    public static function fromArray(array $data): self
    {
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
        );
    }
}
