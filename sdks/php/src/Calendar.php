<?php

declare(strict_types=1);

namespace Mobiscroll\Connect;

class Calendar
{
    /**
     * @param array<string, mixed> $original
     * @param string $account The connected account this calendar belongs to
     * @param bool $readOnly True when the authenticated connection cannot write to this calendar
     */
    public function __construct(
        public readonly string $provider,
        public readonly string $id,
        public readonly string $title,
        public readonly string $timeZone,
        public readonly string $color,
        public readonly string $description,
        public readonly array $original,
        public readonly string $account = '',
        public readonly bool $readOnly = false,
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
            is_string($data['account'] ?? null) ? $data['account'] : '',
            (bool)($data['readOnly'] ?? false),
        );
    }
}
