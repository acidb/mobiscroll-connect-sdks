<?php

declare(strict_types=1);

namespace Mobiscroll\Connect;

class Calendar
{
    /**
     * @param array<string, mixed> $original
     */
    public function __construct(
        public readonly string $provider,
        public readonly string $id,
        public readonly string $title,
        public readonly string $timeZone,
        public readonly string $color,
        public readonly string $description,
        public readonly array $original,
    ) {
    }

    /**
     * @param array<string, mixed> $data
     */
    public static function fromArray(array $data): self
    {
        return new self(
            $data['provider'] ?? throw new \InvalidArgumentException('provider is required'),
            $data['id'] ?? throw new \InvalidArgumentException('id is required'),
            $data['title'] ?? '',
            $data['timeZone'] ?? 'UTC',
            $data['color'] ?? '',
            $data['description'] ?? '',
            $data['original'] ?? [],
        );
    }
}
