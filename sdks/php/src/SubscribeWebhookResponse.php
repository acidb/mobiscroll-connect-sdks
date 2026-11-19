<?php

declare(strict_types=1);

namespace Mobiscroll\Connect;

class SubscribeWebhookResponse
{
    /**
     * @param bool|null $singleEvents The mode the channel was created with: `false` is series mode, `true` is
     *   every occurrence; `null` when the response does not carry it (unknown, not series mode)
     */
    public function __construct(
        public readonly bool $success,
        public readonly string $provider,
        public readonly WebhookSubscription $subscription,
        public readonly string $serverWebhookUrl,
        public readonly string $channelId,
        public readonly ?bool $singleEvents = null,
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
            isset($data['singleEvents']) && is_bool($data['singleEvents']) ? $data['singleEvents'] : null,
        );
    }
}
