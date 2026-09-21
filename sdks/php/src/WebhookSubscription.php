<?php

declare(strict_types=1);

namespace Mobiscroll\Connect;

class WebhookSubscription
{
    public function __construct(
        public readonly string $channelId,
        public readonly ?string $resourceId = null,
        public readonly ?string $expiration = null,
    ) {
    }

    /**
     * @param array<string, mixed> $data
     */
    public static function fromArray(array $data): self
    {
        return new self(
            $data['channelId'] ?? throw new \InvalidArgumentException('channelId is required'),
            $data['resourceId'] ?? null,
            $data['expiration'] ?? null,
        );
    }
}
