<?php

declare(strict_types=1);

namespace Mobiscroll\Connect;

/**
 * A changed event in a webhook delivery: the calendar event plus what happened to it.
 */
class WebhookEvent extends CalendarEvent
{
    /**
     * @param string|null $changeType 'created', 'updated' or 'deleted'
     */
    public function __construct(CalendarEvent $event, public readonly ?string $changeType = null)
    {
        parent::__construct(...get_object_vars($event));
    }

    /**
     * Missing `provider` and `calendarId` default to the delivery's, and a missing `id` to an empty string.
     *
     * @param array<string, mixed> $data
     */
    public static function fromArray(array $data, string $provider = '', string $calendarId = ''): self
    {
        $event = CalendarEvent::fromArray($data + ['provider' => $provider, 'calendarId' => $calendarId, 'id' => '']);
        $changeType = $data['changeType'] ?? null;

        return new self($event, is_string($changeType) ? $changeType : null);
    }
}
