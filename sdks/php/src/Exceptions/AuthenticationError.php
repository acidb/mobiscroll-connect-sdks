<?php

declare(strict_types=1);

namespace Mobiscroll\Connect\Exceptions;

class AuthenticationError extends MobiscrollConnectException
{
    public function __construct(string $message = 'Authentication failed')
    {
        parent::__construct($message, 'AUTHENTICATION_ERROR', 401);
    }
}
