<?php

declare(strict_types=1);

namespace Mobiscroll\Connect\Tests\Unit;

use Mobiscroll\Connect\{TokenResponse};

class AuthTest extends BaseTestCase
{

    public function testGenerateAuthUrlWithUserId(): void
    {
        $url = $this->client->auth()->generateAuthUrl(userId: 'user-123');

        $this->assertStringContainsString('https://connect.mobiscroll.com/api/oauth/authorize', $url);
        $this->assertStringContainsString('client_id=test-client-id', $url);
        $this->assertStringContainsString('response_type=code', $url);
        $this->assertStringContainsString('user_id=user-123', $url);
        $this->assertStringContainsString('scope=calendar', $url);
        $this->assertStringContainsString('redirect_uri=', $url);
    }

    public function testGenerateAuthUrlWithAllParams(): void
    {
        $url = $this->client->auth()->generateAuthUrl(
            userId: 'user-123',
            scope: 'calendar+contacts',
            state: 'test-state',
            providers: 'google,microsoft'
        );

        $this->assertStringContainsString('user_id=user-123', $url);
        $this->assertStringContainsString('state=test-state', $url);
        $this->assertStringContainsString('providers=google%2Cmicrosoft', $url);
    }

    public function testSetCredentials(): void
    {
        $token = new TokenResponse(
            access_token: 'test-token',
            refresh_token: 'test-refresh',
            expires_in: 3600,
        );

        $this->client->auth()->setCredentials($token);
        $this->assertTrue(true); // If no exception thrown, test passes
    }
}
