<?php

declare(strict_types=1);

namespace Mobiscroll\Connect;

/**
 * A changed event in a webhook delivery: the event's fields plus what happened to it.
 *
 * It mirrors `CalendarEvent`, except that `conference` is a `WebhookConference` object, as deliveries
 * carry it. Deleted Microsoft, Apple and CalDAV events arrive as placeholders without `calendarId` and
 * `original`; rely only on `id`, `provider` and `changeType` for a deleted event.
 */
class WebhookEvent
{
    /**
     * @param array<string, mixed>|null $attendees
     * @param array<string, mixed>|null $custom
     * @param array<string, mixed> $original Provider-native event; empty on deleted placeholders
     * @param array<string, mixed>|null $conferenceData
     * @param string|null $lastModified ISO 8601 timestamp of the last modification
     * @param string|null $changeType 'created', 'updated' or 'deleted'
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
        public readonly ?WebhookConference $conference = null,
        public readonly ?string $availability = null,
        public readonly ?string $privacy = null,
        public readonly ?string $status = null,
        public readonly ?string $link = null,
        public readonly array $original = [],
        public readonly ?string $description = null,
        public readonly ?array $conferenceData = null,
        public readonly ?string $lastModified = null,
        public readonly ?string $changeType = null,
    ) {
    }

    /**
     * Missing `provider` and `calendarId` default to the delivery's, and a missing `id` to an empty string.
     *
     * @param array<string, mixed> $data
     */
    public static function fromArray(array $data, string $provider = '', string $calendarId = ''): self
    {
        $fields = $data;
        unset($fields['conference']);
        foreach (['provider' => $provider, 'calendarId' => $calendarId, 'id' => ''] as $key => $fallback) {
            $fields[$key] ??= $fallback;
        }
        $event = CalendarEvent::fromArray($fields);
        $changeType = $data['changeType'] ?? null;

        return new self(
            provider: $event->provider,
            id: $event->id,
            calendarId: $event->calendarId,
            title: $event->title,
            start: $event->start,
            end: $event->end,
            allDay: $event->allDay,
            recurringEventId: $event->recurringEventId,
            color: $event->color,
            location: $event->location,
            attendees: $event->attendees,
            custom: $event->custom,
            conference: WebhookConference::fromValue($data['conference'] ?? null),
            availability: $event->availability,
            privacy: $event->privacy,
            status: $event->status,
            link: $event->link,
            original: $event->original,
            description: $event->description,
            conferenceData: $event->conferenceData,
            lastModified: $event->lastModified,
            changeType: is_string($changeType) ? $changeType : null,
        );
    }
}
