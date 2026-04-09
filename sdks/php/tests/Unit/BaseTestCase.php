<?php

declare(strict_types=1);

namespace Mobiscroll\Connect\Tests\Unit;

use PHPUnit\Framework\TestCase;
use Mobiscroll\Connect\MobiscrollConnectClient;

abstract class BaseTestCase extends TestCase
{
    protected MobiscrollConnectClient $client;

    protected function setUp(): void
    {
        $this->client = new MobiscrollConnectClient(
            clientId: 'test-client-id',
            clientSecret: 'test-client-secret',
            redirectUri: 'http://localhost:3000/callback',
        );
    }
}
