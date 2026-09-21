<?php

declare(strict_types=1);

namespace Mobiscroll\Connect\Resources;

use Mobiscroll\Connect\{ApiClient, SubscribeWebhookResponse, UnsubscribeWebhookResponse};

class Webhooks
{
    public function __construct(private ApiClient $apiClient)
    {
    }

    /**
     * Subscribe to change notifications for a calendar.
     *
     * @param array<string, mixed> $params Subscription data:
     *   - provider: string ('google'|'microsoft'|'apple'|'caldav') REQUIRED
     *   - calendarId: string Calendar ID REQUIRED
     *   - channelId?: string Client-supplied channel ID (auto-generated server-side if omitted)
     *   - expiration?: int Provider-specific expiration timestamp (ms epoch)
     * @return SubscribeWebhookResponse
     */
    public function subscribeWebhook(array $params): SubscribeWebhookResponse
    {
        foreach (['provider', 'calendarId'] as $required) {
            if (!isset($params[$required]) || $params[$required] === '') {
                throw new \InvalidArgumentException("{$required} is required to subscribe to a webhook.");
            }
        }

        $response = $this->apiClient->post('/subscribe-webhook', $params);

        /** @var array<string, mixed> $data */
        $data = is_array($response) ? $response : [];
        return SubscribeWebhookResponse::fromArray($data);
    }

    /**
     * Unsubscribe from change notifications for a calendar.
     *
     * @param array<string, mixed> $params Unsubscription data:
     *   - provider: string REQUIRED
     *   - channelId: string REQUIRED
     *   - resourceId?: string Required by some providers (e.g. Google) to fully unsubscribe
     * @return UnsubscribeWebhookResponse
     */
    public function unsubscribeWebhook(array $params): UnsubscribeWebhookResponse
    {
        foreach (['provider', 'channelId'] as $required) {
            if (!isset($params[$required]) || $params[$required] === '') {
                throw new \InvalidArgumentException("{$required} is required to unsubscribe from a webhook.");
            }
        }

        $response = $this->apiClient->post('/unsubscribe-webhook', $params);

        /** @var array<string, mixed> $data */
        $data = is_array($response) ? $response : [];
        return UnsubscribeWebhookResponse::fromArray($data);
    }
}
