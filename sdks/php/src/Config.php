<?php

declare(strict_types=1);

namespace Mobiscroll\Connect;

class Config
{
    public function __construct(
        public readonly string $clientId,
        public readonly string $clientSecret,
        public readonly string $redirectUri,
    ) {
    }
}
