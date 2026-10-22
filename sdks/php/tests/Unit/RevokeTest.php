<?php

declare(strict_types=1);

namespace Mobiscroll\Connect\Tests\Unit;

use GuzzleHttp\Client as GuzzleClient;
use GuzzleHttp\Exception\RequestException;
use GuzzleHttp\Handler\MockHandler;
use GuzzleHttp\HandlerStack;
use GuzzleHttp\Middleware;
use GuzzleHttp\Psr7\Request;
use GuzzleHttp\Psr7\Response;
use Mobiscroll\Connect\{ApiClient, RevokeResponse, TokenResponse};
use Mobiscroll\Connect\Exceptions\{AuthenticationError, ValidationError};
use Psr\Http\Message\RequestInterface;

class RevokeTest extends BaseTestCase
{
    /** @var array<int, array{request: RequestInterface}> */
    private array $history = [];

    /**
     * Swaps ApiClient's Guzzle client for one bound to a MockHandler that records every request.
     *
     * @param array<int, Response|RequestException> $responses
     */
    private function mockHttpClient(array $responses): MockHandler
    {
        $mockHandler = new MockHandler($responses);
        $handlerStack = HandlerStack::create($mockHandler);
        $handlerStack->push(Middleware::history($this->history));

        $apiClient = $this->apiClient();
        (new \ReflectionObject($apiClient))
            ->getProperty('httpClient')
            ->setValue($apiClient, new GuzzleClient(['handler' => $handlerStack]));

        return $mockHandler;
    }

    private function apiClient(): ApiClient
    {
        $apiClient = (new \ReflectionObject($this->client))->getProperty('apiClient')->getValue($this->client);
        if (!$apiClient instanceof ApiClient) {
            throw new \RuntimeException('apiClient property is not an ApiClient');
        }
        return $apiClient;
    }

    private function setStoredCredentials(?string $refreshToken): void
    {
        $this->apiClient()->setCredentials(new TokenResponse(
            access_token: 'stored-access',
            expires_in: 3600,
            refresh_token: $refreshToken,
        ));
    }

    private function successResponse(): Response
    {
        return new Response(200, ['Content-Type' => 'application/json'], '{"success":true}');
    }

    /**
     * @return array<string, mixed>
     */
    private function requestBody(int $index): array
    {
        /** @var array<string, mixed> $body */
        $body = json_decode((string)$this->history[$index]['request']->getBody(), true);
        return $body;
    }

    public function testRevokeExplicitTokenKeepsStoredCredentials(): void
    {
        $this->mockHttpClient([$this->successResponse()]);
        $this->setStoredCredentials('stored-refresh');

        $result = $this->client->auth()->revoke('explicit-token');

        $this->assertInstanceOf(RevokeResponse::class, $result);
        $this->assertTrue($result->success);
        $this->assertCount(1, $this->history);
        $request = $this->history[0]['request'];
        $this->assertSame('POST', $request->getMethod());
        $this->assertSame('oauth/revoke', $request->getUri()->getPath());
        $this->assertSame('application/json', $request->getHeaderLine('Content-Type'));
        $this->assertSame(['token' => 'explicit-token'], $this->requestBody(0));
        $this->assertSame('stored-refresh', $this->apiClient()->getCredentials()['refresh_token'] ?? null);
    }

    public function testRevokeDefaultsToStoredRefreshTokenAndClearsCredentials(): void
    {
        $this->mockHttpClient([$this->successResponse()]);
        $this->setStoredCredentials('stored-refresh');

        $result = $this->client->auth()->revoke();

        $this->assertTrue($result->success);
        $this->assertSame(['token' => 'stored-refresh'], $this->requestBody(0));
        $this->assertNull($this->apiClient()->getCredentials());
    }

    public function testRevokeFallsBackToStoredAccessToken(): void
    {
        $this->mockHttpClient([$this->successResponse()]);
        $this->setStoredCredentials(null);

        $this->client->auth()->revoke();

        $this->assertSame(['token' => 'stored-access'], $this->requestBody(0));
        $this->assertNull($this->apiClient()->getCredentials());
    }

    public function testRevokeWithoutAnyTokenThrowsWithoutRequest(): void
    {
        $this->mockHttpClient([$this->successResponse()]);

        try {
            $this->client->auth()->revoke();
            $this->fail('Expected ValidationError');
        } catch (ValidationError $e) {
            $this->assertCount(0, $this->history);
        }
    }

    public function testRevokeNeverRefreshesOn401(): void
    {
        $this->mockHttpClient([
            new RequestException(
                'Unauthorized',
                new Request('POST', 'oauth/revoke'),
                new Response(401, [], '{"message":"Unauthorized"}'),
            ),
            new Response(200, [], '{"access_token":"minted","refresh_token":"minted-refresh"}'),
        ]);
        $this->setStoredCredentials('stored-refresh');

        try {
            $this->client->auth()->revoke();
            $this->fail('Expected AuthenticationError');
        } catch (AuthenticationError $e) {
            $this->assertCount(1, $this->history);
            $this->assertSame('stored-refresh', $this->apiClient()->getCredentials()['refresh_token'] ?? null);
        }
    }
}
