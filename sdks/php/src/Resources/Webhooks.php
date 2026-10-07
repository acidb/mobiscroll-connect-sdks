<?php

declare(strict_types=1);

namespace Mobiscroll\Connect\Resources;

use Mobiscroll\Connect\{
    ApiClient,
    SubscribeWebhookResponse,
    UnsubscribeWebhookResponse,
    WebhookDelivery,
    WebhookKeyStore,
    WebhookVerifier,
};
use Mobiscroll\Connect\Exceptions\WebhookVerificationError;
use Psr\Http\Message\MessageInterface;

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

    /**
     * Verify that a webhook delivery came from Mobiscroll Connect, and parse it.
     *
     * Fetches the public keys from `/.well-known/webhook-keys` on first use and caches them for the
     * whole process (and in `webhookKeyCache` when configured), refreshing them as the endpoint's
     * `Cache-Control` allows. When no signature matches, it re-fetches the keys once (at most once a
     * minute) before rejecting, so a key rotation never rejects genuine deliveries. `webhookPublicKey`
     * from the config is used only when the endpoint cannot be reached.
     *
     * @param string $payload The raw request body, exactly as received; not a decoded array
     * @param array<string, string|array<int, string>|null>|MessageInterface $headers The request headers, any casing
     * @return WebhookDelivery The parsed delivery
     * @throws WebhookVerificationError When the delivery is not genuine; respond with a 4xx
     */
    public function verifyWebhook(string $payload, array|MessageInterface $headers): WebhookDelivery
    {
        $config = $this->apiClient->getConfig();
        $cache = $config->webhookKeyCache;
        $store = WebhookKeyStore::forUrl(WebhookKeyStore::keysUrl($this->apiClient->getBaseUri()));
        /** @param array<int, string> $keys */
        $verify = static function (array $keys) use ($payload, $headers, $config): void {
            $pinnedKey = $config->webhookPublicKey;
            WebhookVerifier::verifyWebhookSignature(
                $payload,
                $headers,
                $keys !== [] || $pinnedKey === null || $pinnedKey === '' ? $keys : [$pinnedKey],
                now: (int)WebhookKeyStore::now(),
            );
        };

        try {
            $verify($store->getKeys($cache));
        } catch (WebhookVerificationError $e) {
            $retryable = in_array(
                $e->getReason(),
                [WebhookVerificationError::NO_MATCHING_SIGNATURE, WebhookVerificationError::NO_PUBLIC_KEYS],
                true,
            );
            if (!$retryable || !$store->refreshAfterMismatch($cache)) {
                throw $e;
            }
            $verify($store->getKeys($cache));
        }

        try {
            $data = json_decode($payload, true, 512, JSON_THROW_ON_ERROR);
            if (!is_array($data) || array_is_list($data)) {
                throw new \UnexpectedValueException('Webhook payload is not a JSON object');
            }
            /** @var array<string, mixed> $data */
            return WebhookDelivery::fromArray($data);
        } catch (\Exception | \TypeError $e) {
            throw new WebhookVerificationError(
                'Webhook payload is not a valid delivery: ' . $e->getMessage(),
                WebhookVerificationError::INVALID_PAYLOAD,
            );
        }
    }
}
