<?php

declare(strict_types=1);

namespace Mobiscroll\Connect\Resources;

use Mobiscroll\Connect\ApiClient;

class Calendars
{
    public function __construct(private ApiClient $apiClient)
    {
    }

    /**
     * Return calendars list response from the API.
     *
     * @return array<int, array<string, mixed>>
     */
    public function list(): array
    {
        $response = $this->apiClient->get('/calendars');

        /** @var array<int, array<string, mixed>> $calendars */
        $calendars = is_array($response) ? $response : [];
        return $calendars;
    }
}
