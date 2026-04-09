<?php

declare(strict_types=1);

namespace Mobiscroll\Connect\Exceptions;

class NotFoundError extends MobiscrollConnectException
{
    public function __construct(string $message = 'Resource not found')
    {
        parent::__construct($message, 'NOT_FOUND_ERROR', 404);
    }
}
