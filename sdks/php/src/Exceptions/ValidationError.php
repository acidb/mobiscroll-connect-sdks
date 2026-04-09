<?php

declare(strict_types=1);

namespace Mobiscroll\Connect\Exceptions;

class ValidationError extends MobiscrollConnectException
{
    /**
     * @param array<string, array<string>> $details
     */
    public function __construct(
        string $message = 'Validation failed',
        private array $details = []
    ) {
        parent::__construct($message, 'VALIDATION_ERROR', 400);
    }

    /**
     * @return array<string, array<string>>
     */
    public function getDetails(): array
    {
        return $this->details;
    }
}
