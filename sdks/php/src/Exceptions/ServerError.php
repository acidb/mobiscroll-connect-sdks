<?php

declare(strict_types=1);

namespace Mobiscroll\Connect\Exceptions;

class ServerError extends MobiscrollConnectException
{
    public function __construct(
        string $message = 'Server error',
        private int $statusCode = 500
    ) {
        parent::__construct($message, 'SERVER_ERROR', 500);
    }

    public function getStatusCode(): int
    {
        return $this->statusCode;
    }
}
