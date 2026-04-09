<?php

declare(strict_types=1);

namespace Mobiscroll\Connect\Exceptions;

class RateLimitError extends MobiscrollConnectException
{
    public function __construct(
        string $message = 'Rate limit exceeded',
        private ?int $retryAfter = null
    ) {
        parent::__construct($message, 'RATE_LIMIT_ERROR', 429);
    }

    public function getRetryAfter(): ?int
    {
        return $this->retryAfter;
    }
}
