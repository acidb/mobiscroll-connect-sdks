<?php

declare(strict_types=1);

namespace Mobiscroll\Connect;

class EventsListResponse
{
    /**
     * @param CalendarEvent[] $events
     * @param array<string, string>|null $info Messages keyed by provider name, set when a provider could not be
     *   read and, with `eventIds`, when no connected account can read a calendar (that calendar's ids are then
     *   left out of `events`); `null` when the response has none
     */
    public function __construct(
        public readonly array $events,
        public readonly ?int $pageSize = null,
        public readonly ?string $nextPageToken = null,
        public readonly ?array $info = null,
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
            isset($data['info']) && is_array($data['info']) ? $data['info'] : null,
        );
    }
}
