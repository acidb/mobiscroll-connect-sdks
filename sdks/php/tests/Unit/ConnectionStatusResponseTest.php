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
        ];

        $response = ConnectionStatusResponse::fromArray($data);

        $this->assertFalse($response->limitReached);
        $this->assertArrayHasKey('google', $response->connections);
        $this->assertCount(1, $response->connections['google']);
        $this->assertSame('user@gmail.com', $response->connections['google'][0]['id']);
    }

    public function testFromArrayCarriesCalendarPermission(): void
    {
        $data = [
            'connections' => [
                'google' => [
                    [
                        'id' => 'granted@gmail.com',
                        'grantedScopes' => ['openid', 'https://www.googleapis.com/auth/calendar'],
                        'calendarPermissionGranted' => true,
                    ],
                    [
                        'id' => 'withheld@gmail.com',
                        'grantedScopes' => ['openid', 'https://www.googleapis.com/auth/userinfo.email'],
                        'calendarPermissionGranted' => false,
                    ],
                ],
                'apple' => [
                    ['id' => 'user@icloud.com', 'grantedScopes' => [], 'calendarPermissionGranted' => null],
                ],
            ],
            'limitReached' => false,
        ];

        $response = ConnectionStatusResponse::fromArray($data);

        $this->assertTrue($response->connections['google'][0]['calendarPermissionGranted']);
        $this->assertFalse($response->connections['google'][1]['calendarPermissionGranted']);
        $this->assertNotContains(
            'https://www.googleapis.com/auth/calendar',
            $response->connections['google'][1]['grantedScopes']
        );
        $this->assertNull($response->connections['apple'][0]['calendarPermissionGranted']);
    }

    public function testFromArrayDefaults(): void
    {
        $response = ConnectionStatusResponse::fromArray([]);

        $this->assertSame([], $response->connections);
        $this->assertFalse($response->limitReached);
    }

    public function testFromArrayLimitReached(): void
    {
        $response = ConnectionStatusResponse::fromArray([
            'connections' => ['google' => [['id' => 'a@gmail.com']]],
            'limitReached' => true,
        ]);

        $this->assertTrue($response->limitReached);
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
