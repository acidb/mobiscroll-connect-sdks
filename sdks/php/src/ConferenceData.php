<?php

declare(strict_types=1);

namespace Mobiscroll\Connect;

/**
 * The conference of an event, with the same fields on every provider.
 */
class ConferenceData
{
    /**
     * @param string|null $provider conference system: `hangoutsMeet`, `google-meet`, `teams` or `caldav`
     * @param string|null $url the join URL, the same value as the event's `conference`
     * @param bool|null $autoGenerate whether a meeting link was requested on create or update
     * @param array<string, mixed>|null $original the conference data exactly as the calendar provider returns it:
     *        Google's `conferenceData`, Microsoft's `onlineMeeting`; null for Apple and CalDAV
     */
    public function __construct(
        public readonly ?string $provider = null,
        public readonly ?string $url = null,
        public readonly ?bool $autoGenerate = null,
        public readonly ?array $original = null,
    ) {
    }

    /**
     * @param array<string, mixed> $data
     */
    public static function fromArray(array $data): self
    {
        $original = $data['original'] ?? null;

        return new self(
            $data['provider'] ?? null,
            $data['url'] ?? null,
            is_bool($data['autoGenerate'] ?? null) ? $data['autoGenerate'] : null,
            is_array($original) && !array_is_list($original) ? $original : null,
        );
    }
}
