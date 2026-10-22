<?php

declare(strict_types=1);

namespace Mobiscroll\Connect;

class RevokeResponse
{
    public function __construct(
        public readonly bool $success,
    ) {
    }

    /**
     * @param array<string, mixed> $data
     */
    public static function fromArray(array $data): self
    {
        return new self((bool)($data['success'] ?? false));
    }
}
