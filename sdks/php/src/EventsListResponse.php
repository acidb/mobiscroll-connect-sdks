<?php

declare(strict_types=1);

namespace Mobiscroll\Connect;

class EventsListResponse
{
    /**
     * @param CalendarEvent[] $events
     */
    public function __construct(
        public readonly array $events,
        public readonly ?int $pageSize = null,
        public readonly ?string $nextPageToken = null,
    ) {
    }

    /**
     * @param array<string, mixed> $data
     */
    public static function fromArray(array $data): self
    {
        $events = array_map(
            fn(array $event) => CalendarEvent::fromArray($event),
            $data['events'] ?? []
        );

        return new self(
            $events,
            $data['pageSize'] ?? null,
            $data['nextPageToken'] ?? null,
        );
    }
}
