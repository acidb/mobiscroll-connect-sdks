<?php

declare(strict_types=1);

namespace Mobiscroll\Connect\Tests\Unit;

use GuzzleHttp\Client as GuzzleClient;
use GuzzleHttp\Exception\RequestException;
use GuzzleHttp\Handler\MockHandler;
use GuzzleHttp\HandlerStack;
use GuzzleHttp\Psr7\Request;
use GuzzleHttp\Psr7\Response;
use Mobiscroll\Connect\{SubscribeWebhookResponse, TokenResponse, UnsubscribeWebhookResponse};
use Mobiscroll\Connect\Exceptions\{AuthenticationError, ServerError, ValidationError};

class WebhooksTest extends BaseTestCase
{
    /**
     * Replaces the real Guzzle client inside ApiClient with one bound to a
     * MockHandler, so requests never hit the network, and primes a bearer
     * token so calls pass through the auth-header step.
     *
     * @param array<int, Response|RequestException> $responses queued in consumption order
     */
    private function mockHttpClient(array $responses): MockHandler
    {
        $mockHandler = new MockHandler($responses);
        $handlerStack = HandlerStack::create($mockHandler);
        $httpClient = new GuzzleClient(['handler' => $handlerStack]);

        $clientRef = new \ReflectionObject($this->client);
        $apiClientProp = $clientRef->getProperty('apiClient');
        $apiClient = $apiClientProp->getValue($this->client);

        if ($apiClient === null) {
            throw new \RuntimeException('apiClient property is null');
        }

        $apiClientRef = new \ReflectionObject($apiClient);
        $httpClientProp = $apiClientRef->getProperty('httpClient');
        $httpClientProp->setValue($apiClient, $httpClient);

        $apiClient->setCredentials(new TokenResponse(access_token: 'test-token', expires_in: 3600));

        return $mockHandler;
    }

    public function testSubscribeWebhookSuccess(): void
    {
        $body = (string)json_encode([
            'success' => true,
            'provider' => 'google',
            'subscription' => [
                'channelId' => 'chan-123',
                'resourceId' => 'res-456',
                'expiration' => '2024-06-15T10:00:00Z',
            ],
            'serverWebhookUrl' => 'https://connect.mobiscroll.com/api/webhook/google',
            'channelId' => 'chan-123',
        ]);

        $mockHandler = $this->mockHttpClient([new Response(200, [], $body)]);

        $result = $this->client->webhooks()->subscribeWebhook([
            'provider' => 'google',
            'calendarId' => 'primary',
        ]);

        $this->assertInstanceOf(SubscribeWebhookResponse::class, $result);
        $this->assertTrue($result->success);
        $this->assertSame('google', $result->provider);
        $this->assertSame('chan-123', $result->channelId);
        $this->assertSame('chan-123', $result->subscription->channelId);
        $this->assertSame('res-456', $result->subscription->resourceId);
        $this->assertSame('2024-06-15T10:00:00Z', $result->subscription->expiration);
        $this->assertSame('https://connect.mobiscroll.com/api/webhook/google', $result->serverWebhookUrl);

        $lastRequest = $mockHandler->getLastRequest();
        $this->assertNotNull($lastRequest);
        $this->assertSame('POST', $lastRequest->getMethod());
        $this->assertSame('subscribe-webhook', $lastRequest->getUri()->getPath());
    }

    public function testSubscribeWebhookWithOptionalParams(): void
    {
        $body = (string)json_encode([
            'success' => true,
            'provider' => 'caldav',
            'subscription' => ['channelId' => 'my-channel'],
            'serverWebhookUrl' => 'https://connect.mobiscroll.com/api/webhook/caldav',
            'channelId' => 'my-channel',
        ]);

        $mockHandler = $this->mockHttpClient([new Response(200, [], $body)]);

        $result = $this->client->webhooks()->subscribeWebhook([
            'provider' => 'caldav',
            'calendarId' => 'primary',
            'channelId' => 'my-channel',
            'expiration' => 1234567890,
        ]);

        $this->assertNull($result->subscription->resourceId);
        $this->assertNull($result->subscription->expiration);

        $lastRequest = $mockHandler->getLastRequest();
        $this->assertNotNull($lastRequest);
        /** @var array<string, mixed> $sentBody */
        $sentBody = json_decode((string)$lastRequest->getBody(), true);
        $this->assertSame('my-channel', $sentBody['channelId']);
        $this->assertSame(1234567890, $sentBody['expiration']);
    }

    public function testSubscribeWebhookMissingProviderThrows(): void
    {
        $this->expectException(\InvalidArgumentException::class);

        $this->client->webhooks()->subscribeWebhook(['calendarId' => 'primary']);
    }

    public function testSubscribeWebhookMissingCalendarIdThrows(): void
    {
        $this->expectException(\InvalidArgumentException::class);

        $this->client->webhooks()->subscribeWebhook(['provider' => 'google']);
    }

    public function testSubscribeWebhookValidationError(): void
    {
        $request = new Request('POST', 'subscribe-webhook');
        $response = new Response(400, [], (string)json_encode(['message' => 'Unsupported provider']));

        $this->mockHttpClient([new RequestException('Bad Request', $request, $response)]);

        $this->expectException(ValidationError::class);

        $this->client->webhooks()->subscribeWebhook([
            'provider' => 'unsupported',
            'calendarId' => 'primary',
        ]);
    }

    public function testSubscribeWebhookAuthenticationError(): void
    {
        $request = new Request('POST', 'subscribe-webhook');
        $response = new Response(401, [], (string)json_encode(['message' => 'Invalid token']));

        $this->mockHttpClient([new RequestException('Unauthorized', $request, $response)]);

        $this->expectException(AuthenticationError::class);

        $this->client->webhooks()->subscribeWebhook([
            'provider' => 'google',
            'calendarId' => 'primary',
        ]);
    }

    public function testSubscribeWebhookServerError(): void
    {
        $request = new Request('POST', 'subscribe-webhook');
        $response = new Response(500, [], (string)json_encode(['message' => 'Internal error']));

        $this->mockHttpClient([new RequestException('Server Error', $request, $response)]);

        $this->expectException(ServerError::class);

        $this->client->webhooks()->subscribeWebhook([
            'provider' => 'google',
            'calendarId' => 'primary',
        ]);
    }

    public function testUnsubscribeWebhookSuccess(): void
    {
        $body = (string)json_encode(['success' => true]);

        $mockHandler = $this->mockHttpClient([new Response(200, [], $body)]);

        $result = $this->client->webhooks()->unsubscribeWebhook([
            'provider' => 'google',
            'channelId' => 'chan-123',
            'resourceId' => 'res-456',
        ]);

        $this->assertInstanceOf(UnsubscribeWebhookResponse::class, $result);
        $this->assertTrue($result->success);
        $this->assertNull($result->message);

        $lastRequest = $mockHandler->getLastRequest();
        $this->assertNotNull($lastRequest);
        $this->assertSame('unsubscribe-webhook', $lastRequest->getUri()->getPath());
    }

    public function testUnsubscribeWebhookSuccessWithMessageOnProviderFailure(): void
    {
        // Backend still returns success:true with an explanatory message when the
        // provider-side unsubscribe fails (e.g. already-expired subscription), after
        // it has removed the local mapping — callers treat 200 as final regardless.
        $body = (string)json_encode([
            'success' => true,
            'message' => 'Subscription already expired upstream; local mapping removed.',
        ]);

        $this->mockHttpClient([new Response(200, [], $body)]);

        $result = $this->client->webhooks()->unsubscribeWebhook([
            'provider' => 'google',
            'channelId' => 'chan-123',
        ]);

        $this->assertTrue($result->success);
        $this->assertSame('Subscription already expired upstream; local mapping removed.', $result->message);
    }

    public function testUnsubscribeWebhookMissingProviderThrows(): void
    {
        $this->expectException(\InvalidArgumentException::class);

        $this->client->webhooks()->unsubscribeWebhook(['channelId' => 'chan-123']);
    }

    public function testUnsubscribeWebhookMissingChannelIdThrows(): void
    {
        $this->expectException(\InvalidArgumentException::class);

        $this->client->webhooks()->unsubscribeWebhook(['provider' => 'google']);
    }

    public function testUnsubscribeWebhookValidationError(): void
    {
        $request = new Request('POST', 'unsubscribe-webhook');
        $response = new Response(400, [], (string)json_encode(['message' => 'provider not supported']));

        $this->mockHttpClient([new RequestException('Bad Request', $request, $response)]);

        $this->expectException(ValidationError::class);

        $this->client->webhooks()->unsubscribeWebhook([
            'provider' => 'unsupported',
            'channelId' => 'chan-123',
        ]);
    }

    public function testUnsubscribeWebhookAuthenticationError(): void
    {
        $request = new Request('POST', 'unsubscribe-webhook');
        $response = new Response(401, [], (string)json_encode(['message' => 'Invalid token']));

        $this->mockHttpClient([new RequestException('Unauthorized', $request, $response)]);

        $this->expectException(AuthenticationError::class);

        $this->client->webhooks()->unsubscribeWebhook([
            'provider' => 'google',
            'channelId' => 'chan-123',
        ]);
    }

    public function testUnsubscribeWebhookServerError(): void
    {
        $request = new Request('POST', 'unsubscribe-webhook');
        $response = new Response(500, [], (string)json_encode(['message' => 'Internal error']));

        $this->mockHttpClient([new RequestException('Server Error', $request, $response)]);

        $this->expectException(ServerError::class);

        $this->client->webhooks()->unsubscribeWebhook([
            'provider' => 'google',
            'channelId' => 'chan-123',
        ]);
    }
}
