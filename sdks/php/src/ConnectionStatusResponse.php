<?php

declare(strict_types=1);

namespace Mobiscroll\Connect;

class ConnectionStatusResponse
{
    /**
     * @param array<string, array<array{id: string, display?: string}>> $connections
     */
    public function __construct(
        public readonly array $connections,
        public readonly bool $limitReached,
        public readonly ?int $limit = null,
    ) {
    }

    /**
     * @param array<string, mixed> $data
     */
    public static function fromArray(array $data): self
    {
        return new self(
            $data['connections'] ?? [],
            $data['limitReached'] ?? false,
            $data['limit'] ?? null,
        );
    }
}
