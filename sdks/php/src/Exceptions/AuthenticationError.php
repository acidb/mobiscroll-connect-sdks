<?php

declare(strict_types=1);

namespace Mobiscroll\Connect\Exceptions;

class AuthenticationError extends MobiscrollConnectException
{
    public function __construct(string $message = 'Authentication failed', string $codeString = 'AUTHENTICATION_ERROR')
    {
        parent::__construct($message, $codeString, 401);
    }
}
