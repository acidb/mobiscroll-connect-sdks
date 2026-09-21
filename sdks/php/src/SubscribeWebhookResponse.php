<?php

declare(strict_types=1);

namespace Mobiscroll\Connect;

class SubscribeWebhookResponse
{
    public function __construct(
        public readonly bool $success,
        public readonly string $provider,
        public readonly WebhookSubscription $subscription,
        public readonly string $serverWebhookUrl,
        public readonly string $channelId,
    ) {
    }

    /**
     * @param array<string, mixed> $data
     */
    public static function fromArray(array $data): self
    {
        return new self(
            $data['success'] ?? false,
            $data['provider'] ?? throw new \InvalidArgumentException('provider is required'),
            WebhookSubscription::fromArray($data['subscription'] ?? []),
            $data['serverWebhookUrl'] ?? '',
            $data['channelId'] ?? '',
        );
    }
}
