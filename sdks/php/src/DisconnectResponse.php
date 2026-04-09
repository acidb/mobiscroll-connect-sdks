<?php

declare(strict_types=1);

namespace Mobiscroll\Connect;

class DisconnectResponse
{
    public function __construct(
        public readonly bool $success,
        public readonly ?string $message = null,
    ) {
    }

    /**
     * @param array<string, mixed> $data
     */
    public static function fromArray(array $data): self
    {
        return new self(
            $data['success'] ?? false,
            $data['message'] ?? null,
        );
    }
}
