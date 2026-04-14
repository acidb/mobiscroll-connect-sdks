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
            scope: 'read-write',
            state: 'test-state',
            providers: 'google,microsoft'
        );

        $this->assertStringContainsString('user_id=user-123', $url);
        $this->assertStringContainsString('scope=read-write', $url);
        $this->assertStringContainsString('state=test-state', $url);
        $this->assertStringContainsString('providers=google%2Cmicrosoft', $url);
    }

    public function testGenerateAuthUrlDefaultScopeIsCalendar(): void
    {
        $url = $this->client->auth()->generateAuthUrl(userId: 'user-123');
        $this->assertStringContainsString('scope=calendar', $url);
    }

    public function testSetCredentialsRegistersTokenForSubsequentCalls(): void
    {
        $called = false;

        $this->client->onTokensRefreshed(function (TokenResponse $tokens) use (&$called): void {
            $called = true;
        });

        $token = new TokenResponse(
            access_token: 'test-access-token',
            expires_in: 3600,
            refresh_token: 'test-refresh-token',
        );

        $this->client->auth()->setCredentials($token);
        $this->assertFalse($called, 'onTokensRefreshed should not fire on manual setCredentials');
    }

    public function testOnTokensRefreshedCallbackIsInvokable(): void
    {
        $received = null;

        $this->client->onTokensRefreshed(function (TokenResponse $tokens) use (&$received): void {
            $received = $tokens;
        });

        $token = new TokenResponse(
            access_token: 'refreshed-access-token',
            refresh_token: 'refreshed-refresh-token',
            expires_in: 3600,
        );

        $this->assertNull($received, 'Callback should not have fired yet');
        ($this->getCallbackFromClient())($token);
        $this->assertInstanceOf(TokenResponse::class, $received);
        /** @var TokenResponse $received */
        $this->assertSame('refreshed-access-token', $received->access_token);
        $this->assertSame('refreshed-refresh-token', $received->refresh_token);
    }

    /**
     * Uses reflection to retrieve the registered onTokensRefreshed callback from ApiClient.
     *
     * @return callable(TokenResponse): void
     */
    private function getCallbackFromClient(): callable
    {
        $clientRef = new \ReflectionObject($this->client);
        $apiClientProp = $clientRef->getProperty('apiClient');
        $apiClient = $apiClientProp->getValue($this->client);

        if ($apiClient === null) {
            throw new \RuntimeException('apiClient property is null');
        }

        $apiClientRef = new \ReflectionObject($apiClient);
        $callbackProp = $apiClientRef->getProperty('onTokensRefreshed');
        $callback = $callbackProp->getValue($apiClient);

        $this->assertIsCallable($callback, 'onTokensRefreshed callback should be registered');
        return $callback;
    }
}
