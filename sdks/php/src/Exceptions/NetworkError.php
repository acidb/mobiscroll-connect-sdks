<?php

declare(strict_types=1);

namespace Mobiscroll\Connect\Exceptions;

class NetworkError extends MobiscrollConnectException
{
    public function __construct(string $message = 'Network error')
    {
        parent::__construct($message, 'NETWORK_ERROR', 0);
    }
}
