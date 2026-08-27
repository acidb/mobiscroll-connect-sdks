<?php

declare(strict_types=1);

namespace Mobiscroll\Connect\Exceptions;

/**
 * Thrown when no connected account has the calendar access the request needs.
 *
 * The user completed sign-in but did not grant the calendar permission — Google's consent
 * screen presents it as a separate checkbox. This cannot be repaired server-side, because
 * providers only issue permissions at consent time: the accounts returned by
 * {@see self::getAccounts()} have to run the connect flow again and allow access.
 *
 * Extends AuthenticationError, so existing `catch (AuthenticationError $e)` blocks keep working.
 */
class CalendarPermissionError extends AuthenticationError
{
    /**
     * @param array<array{provider: string, account: string}> $accounts
     */
    public function __construct(
        string $message = 'No connected account has calendar access',
        private readonly array $accounts = [],
    ) {
        parent::__construct($message, 'CALENDAR_PERMISSION_REQUIRED');
    }

    /**
     * Connected accounts that withheld calendar access and must reconnect.
     *
     * @return array<array{provider: string, account: string}>
     */
    public function getAccounts(): array
    {
        return $this->accounts;
    }
}
