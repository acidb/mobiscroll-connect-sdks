<?php

declare(strict_types=1);

namespace Mobiscroll\Connect\Tests\Unit;

use Mobiscroll\Connect\{ConnectionStatusResponse, DisconnectResponse};

class ConnectionStatusResponseTest extends BaseTestCase
{
    public function testFromArrayWithConnections(): void
    {
        $data = [
            'connections' => [
                'google' => [
                    ['id' => 'user@gmail.com', 'display' => 'User Gmail'],
                ],
                'microsoft' => [],
            ],
            'limitReached' => false,
            'limit' => 5,
        ];

        $response = ConnectionStatusResponse::fromArray($data);

        $this->assertFalse($response->limitReached);
        $this->assertSame(5, $response->limit);
        $this->assertArrayHasKey('google', $response->connections);
        $this->assertCount(1, $response->connections['google']);
        $this->assertSame('user@gmail.com', $response->connections['google'][0]['id']);
    }

    public function testFromArrayDefaults(): void
    {
        $response = ConnectionStatusResponse::fromArray([]);

        $this->assertSame([], $response->connections);
        $this->assertFalse($response->limitReached);
        $this->assertNull($response->limit);
    }

    public function testFromArrayLimitReached(): void
    {
        $response = ConnectionStatusResponse::fromArray([
            'connections' => ['google' => [['id' => 'a@gmail.com']]],
            'limitReached' => true,
            'limit' => 1,
        ]);

        $this->assertTrue($response->limitReached);
        $this->assertSame(1, $response->limit);
    }

    public function testDisconnectResponseSuccess(): void
    {
        $response = DisconnectResponse::fromArray(['success' => true, 'message' => 'Disconnected']);

        $this->assertTrue($response->success);
        $this->assertSame('Disconnected', $response->message);
    }

    public function testDisconnectResponseDefaults(): void
    {
        $response = DisconnectResponse::fromArray([]);

        $this->assertFalse($response->success);
        $this->assertNull($response->message);
    }

    public function testDisconnectResponseNoMessage(): void
    {
        $response = DisconnectResponse::fromArray(['success' => true]);

        $this->assertTrue($response->success);
        $this->assertNull($response->message);
    }
}
