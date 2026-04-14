<?php

declare(strict_types=1);

namespace Mobiscroll\Connect;

use Mobiscroll\Connect\Resources\{Auth, Calendars, Events};
use Mobiscroll\Connect\TokenResponse;

class MobiscrollConnectClient
{
    private Auth $auth;
    private Calendars $calendars;
    private Events $events;
    private ApiClient $apiClient;

    public function __construct(
        string $clientId,
        string $clientSecret,
        string $redirectUri,
    ) {
        $config = new Config($clientId, $clientSecret, $redirectUri);
        $this->apiClient = new ApiClient($config);

        $this->auth = new Auth($this->apiClient);
        $this->calendars = new Calendars($this->apiClient);
        $this->events = new Events($this->apiClient);
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
