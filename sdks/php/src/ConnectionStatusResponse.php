<?php

declare(strict_types=1);

namespace Mobiscroll\Connect;

class ConnectionStatusResponse
{
    /**
     * Each account also reports the scopes the provider actually granted.
     *
     * `calendarPermissionGranted` is `false` for accounts that connected but withheld
     * calendar access on the provider's consent screen — Google's screen lets the user
     * untick that permission and still complete sign-in. Those accounts list no
     * calendars until the user reconnects and allows it. It is `null` when the question
     * does not apply (Apple and CalDav authenticate with a username and app password) or
     * no scopes were recorded for the account.
     *
     * @param array<string, array<array{id: string, display?: string, grantedScopes: list<string>, calendarPermissionGranted: bool|null}>> $connections
     */
    public function __construct(
        public readonly array $connections,
        public readonly bool $limitReached,
    ) {
    }

    /**
     * @param array<string, mixed> $data
     */
    public static function fromArray(array $data): self
    {
        return new self(
            $data['connections'] ?? [],
            $data['limitReached'] ?? false,
        );
    }
}
