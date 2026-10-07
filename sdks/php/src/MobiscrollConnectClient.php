<?php

declare(strict_types=1);

namespace Mobiscroll\Connect;

use Mobiscroll\Connect\Resources\{Auth, Calendars, Events, Webhooks};
use Mobiscroll\Connect\TokenResponse;
use Psr\SimpleCache\CacheInterface;

class MobiscrollConnectClient
{
    private Auth $auth;
    private Calendars $calendars;
    private Events $events;
    private Webhooks $webhooks;
    private ApiClient $apiClient;

    /**
     * @param string|null $webhookPublicKey Pinned `whpk_` key for `webhooks()->verifyWebhook()`, used only
     *   when the webhook keys endpoint cannot be reached. Replace it on every key rotation.
     * @param CacheInterface|null $webhookKeyCache PSR-16 cache that shares the fetched webhook keys across
     *   requests and workers. Without it, each PHP-FPM request fetches the keys on its first verification.
     */
    public function __construct(
        string $clientId,
        string $clientSecret,
        string $redirectUri,
        ?string $webhookPublicKey = null,
        ?CacheInterface $webhookKeyCache = null,
    ) {
        $config = new Config($clientId, $clientSecret, $redirectUri, $webhookPublicKey, $webhookKeyCache);
        $this->apiClient = new ApiClient($config);

        $this->auth = new Auth($this->apiClient);
        $this->calendars = new Calendars($this->apiClient);
        $this->events = new Events($this->apiClient);
        $this->webhooks = new Webhooks($this->apiClient);
    }

    public function auth(): Auth
    {
        return $this->auth;
    }

    public function calendars(): Calendars
    {
        return $this->calendars;
    }

    public function events(): Events
    {
        return $this->events;
    }

    public function webhooks(): Webhooks
    {
        return $this->webhooks;
    }

    /**
     * Register a callback to be invoked whenever the SDK automatically refreshes
     * the access token. Use this to persist the new tokens so they survive
     * future requests.
     *
     * @param callable(TokenResponse): void $callback
     */
    public function onTokensRefreshed(callable $callback): void
    {
        $this->apiClient->onTokensRefreshed($callback);
    }
}
