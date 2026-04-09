<?php

declare(strict_types=1);

namespace Mobiscroll\Connect;

use Mobiscroll\Connect\Resources\{Auth, Calendars, Events};

class MobiscrollConnectClient
{
    private Auth $auth;
    private Calendars $calendars;
    private Events $events;

    public function __construct(
        string $clientId,
        string $clientSecret,
        string $redirectUri,
    ) {
        $config = new Config($clientId, $clientSecret, $redirectUri);
        $apiClient = new ApiClient($config);

        $this->auth = new Auth($apiClient);
        $this->calendars = new Calendars($apiClient);
        $this->events = new Events($apiClient);
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
}
