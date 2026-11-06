<?php

declare(strict_types=1);

namespace Mobiscroll\Connect;

/**
 * An event in a webhook delivery. Webhook deliveries still create `WebhookEvent` instances, so existing type
 * declarations keep working.
 *
 * @deprecated Use {@see CalendarEvent}, which has `changeType` for webhook deliveries. `WebhookEvent` will be
 *             removed in 2.0.0.
 */
class WebhookEvent extends CalendarEvent
{
    /**
     * Missing `provider` and `calendarId` default to the delivery's, and a missing `id` to an empty string.
     *
     * @param array<string, mixed> $data
     */
    public static function fromArray(array $data, ?string $provider = '', ?string $calendarId = ''): self
    {
        return new self(...get_object_vars(CalendarEvent::fromArray($data, $provider ?? '', $calendarId ?? '')));
    }
}
