<?php

declare(strict_types=1);

namespace Mobiscroll\Connect;

/**
 * A changed event in a webhook delivery: the calendar event plus what happened to it.
 *
 * Deliveries carry `conference` as an object. `fromArray()` converts it to what `CalendarEvent` holds:
 * `conference` is the join URL and `conferenceData` the REST shape. Deleted Microsoft, Apple and CalDAV
 * events arrive as placeholders without `original` (and, in deliveries sent before Connect 1.6.1, without
 * `calendarId`, which is then filled with the delivery's); rely only on `id`, `provider`, `calendarId` and
 * `changeType` for a deleted event.
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
     * @throws \InvalidArgumentException When `conference` is neither a string, an object nor null
     */
    public static function fromArray(array $data, string $provider = '', string $calendarId = ''): self
    {
        $fields = $data;
        foreach (['provider' => $provider, 'calendarId' => $calendarId, 'id' => ''] as $key => $fallback) {
            $fields[$key] ??= $fallback;
        }
        $event = CalendarEvent::fromArray(self::convertConference($fields, $provider));
        $changeType = $data['changeType'] ?? null;

        return new self($event, is_string($changeType) ? $changeType : null);
    }

    /**
     * @param array<string, mixed> $fields
     * @return array<string, mixed>
     */
    private static function convertConference(array $fields, string $deliveryProvider): array
    {
        $conference = $fields['conference'] ?? null;
        if ($conference === null || is_string($conference)) {
            return $fields;
        }
        if (!is_array($conference) || ($conference !== [] && array_is_list($conference))) {
            throw new \InvalidArgumentException('conference must be a string or an object');
        }

        $url = $conference['url'] ?? null;
        $fields['conference'] = is_string($url) && $url !== '' ? $url : null;
        if (($fields['conferenceData'] ?? null) !== null) {
            return $fields;
        }

        $eventProvider = $fields['provider'] ?? null;
        $conferenceProvider = $conference['provider'] ?? null;
        if (!is_string($conferenceProvider) || $conferenceProvider === '') {
            $conferenceProvider = self::inferConferenceProvider(
                is_string($eventProvider) && $eventProvider !== '' ? $eventProvider : $deliveryProvider,
            );
        }

        $conferenceData = [];
        if ($conferenceProvider !== null) {
            $conferenceData['provider'] = $conferenceProvider;
        }
        if (is_bool($conference['autoGenerate'] ?? null)) {
            $conferenceData['autoGenerate'] = $conference['autoGenerate'];
        }
        $data = $conference['data'] ?? null;
        if (is_array($data) && !array_is_list($data)) {
            foreach ($data as $key => $value) {
                $conferenceData[$key] = $value;
            }
        }
        if ($conferenceData !== []) {
            $fields['conferenceData'] = $conferenceData;
        }

        return $fields;
    }

    private static function inferConferenceProvider(string $calendarProvider): ?string
    {
        return match (strtolower($calendarProvider)) {
            'google' => 'google-meet',
            'microsoft' => 'teams',
            'apple', 'caldav' => 'caldav',
            default => null,
        };
    }
}
