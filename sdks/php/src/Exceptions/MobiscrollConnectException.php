<?php

declare(strict_types=1);

namespace Mobiscroll\Connect\Exceptions;

use Exception;

class MobiscrollConnectException extends Exception
{
    public function __construct(
        string $message = '',
        private string $code_string = 'MOBISCROLL_ERROR',
        int $code = 0,
        ?Exception $previous = null
    ) {
        parent::__construct($message, $code, $previous);
    }

    public function getCodeString(): string
    {
        return $this->code_string;
    }
}
