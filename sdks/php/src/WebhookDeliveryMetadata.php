<?php

declare(strict_types=1);

namespace Mobiscroll\Connect;

class WebhookDeliveryMetadata
{
    public function __construct(
        public readonly string $channelId,
        public readonly int $eventCount,
        public readonly ?bool $isInitialSync = null,
    ) {
    }

    /**
     * @param array<string, mixed> $data
     */
    public static function fromArray(array $data, int $defaultEventCount = 0): self
    {
        $channelId = $data['channelId'] ?? null;
        $eventCount = $data['eventCount'] ?? null;
        $isInitialSync = $data['isInitialSync'] ?? null;

        return new self(
            is_string($channelId) ? $channelId : '',
            is_int($eventCount) ? $eventCount : $defaultEventCount,
            is_bool($isInitialSync) ? $isInitialSync : null,
        );
    }
}
