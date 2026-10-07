<?php

declare(strict_types=1);

namespace Mobiscroll\Connect\Tests\Unit;

use GuzzleHttp\Client as GuzzleClient;
use GuzzleHttp\Exception\ConnectException;
use GuzzleHttp\HandlerStack;
use GuzzleHttp\Promise\Create;
use GuzzleHttp\Promise\PromiseInterface;
use GuzzleHttp\Psr7\Response;
use GuzzleHttp\Psr7\ServerRequest;
use Mobiscroll\Connect\Exceptions\{MobiscrollConnectException, WebhookVerificationError};
use Mobiscroll\Connect\{MobiscrollConnectClient, WebhookDelivery, WebhookEvent, WebhookKeyStore, WebhookVerifier};
use PHPUnit\Framework\Attributes\DataProvider;
use PHPUnit\Framework\TestCase;
use Psr\Http\Message\RequestInterface;
use Psr\Http\Message\ResponseInterface;
use Psr\SimpleCache\CacheInterface;

/**
 * @phpstan-type Vector array{name: string, valid: bool, body: string, headers: array<string, string>, publicKeys: array<int, string>, now: int}
 */
class WebhookVerificationTest extends TestCase
{
    private const KEYS_URL = 'https://connect.mobiscroll.com/.well-known/webhook-keys';

    /** @var array{keys: array{active: string, previous: string, unrelated: string}, cases: array<int, Vector>}|null */
    private static ?array $vectors = null;

    private float $clock = 0.0;
    /** @var array<int, array{request: RequestInterface, options: array<string, mixed>}> */
    private array $requests = [];
    /** @var \Closure(RequestInterface): (ResponseInterface|\Throwable) */
    private \Closure $respond;

    /**
     * @return array{keys: array{active: string, previous: string, unrelated: string}, cases: array<int, Vector>}
     */
    private static function vectors(): array
    {
        if (self::$vectors === null) {
            /** @var array{keys: array{active: string, previous: string, unrelated: string}, cases: array<int, Vector>} $decoded */
            $decoded = json_decode((string)file_get_contents(__DIR__ . '/fixtures/webhook-vectors.json'), true);
            self::$vectors = $decoded;
        }
        return self::$vectors;
    }

    /**
     * @return Vector
     */
    private static function vector(string $name): array
    {
        foreach (self::vectors()['cases'] as $vector) {
            if ($vector['name'] === $name) {
                return $vector;
            }
        }
        throw new \RuntimeException("Missing vector: {$name}");
    }

    /**
     * @return array<string, array{Vector}>
     */
    public static function vectorCases(): array
    {
        $cases = [];
        foreach (self::vectors()['cases'] as $vector) {
            $cases[$vector['name']] = [$vector];
        }
        return $cases;
    }

    protected function setUp(): void
    {
        $this->clock = (float)self::vector('valid single signature')['now'];
        $this->requests = [];
        $this->respond = fn (RequestInterface $request): ResponseInterface => self::keysResponse([self::key('active')]);
        $this->startRequest();
    }

    protected function tearDown(): void
    {
        WebhookKeyStore::reset();
    }

    /**
     * Clears the process-wide key cache, as a new PHP-FPM request would start, and wires the mocked
     * HTTP client and clock into it.
     */
    private function startRequest(): void
    {
        WebhookKeyStore::reset();

        $handler = function (RequestInterface $request, array $options): PromiseInterface {
            /** @var array<string, mixed> $options */
            $this->requests[] = ['request' => $request, 'options' => $options];
            $result = ($this->respond)($request);
            return $result instanceof \Throwable ? Create::rejectionFor($result) : Create::promiseFor($result);
        };
        $store = new \ReflectionClass(WebhookKeyStore::class);
        $store->setStaticPropertyValue('httpClient', new GuzzleClient(['handler' => HandlerStack::create($handler)]));
        $store->setStaticPropertyValue('clock', fn (): float => $this->clock);
    }

    private static function key(string $name): string
    {
        return self::vectors()['keys'][$name];
    }

    /**
     * @param array<int, string> $keys
     */
    private static function keysResponse(array $keys, string $cacheControl = 'public, max-age=3600'): Response
    {
        $entries = array_map(
            fn (string $key): array => ['id' => $key, 'alg' => 'ed25519', 'key' => $key, 'status' => 'active'],
            $keys,
        );
        return new Response(200, ['Cache-Control' => $cacheControl], (string)json_encode(['keys' => $entries]));
    }

    private function createClient(?string $webhookPublicKey = null, ?CacheInterface $cache = null): MobiscrollConnectClient
    {
        return new MobiscrollConnectClient(
            clientId: 'id',
            clientSecret: 'secret',
            redirectUri: 'uri',
            webhookPublicKey: $webhookPublicKey,
            webhookKeyCache: $cache,
        );
    }

    private function verify(MobiscrollConnectClient $client, string $vectorName = 'valid single signature'): WebhookDelivery
    {
        $vector = self::vector($vectorName);
        return $client->webhooks()->verifyWebhook($vector['body'], $vector['headers']);
    }

    private function expectReason(string $reason, callable $callback): void
    {
        try {
            $callback();
        } catch (WebhookVerificationError $e) {
            $this->assertSame($reason, $e->getReason());
            return;
        }
        $this->fail("Expected WebhookVerificationError with reason {$reason}");
    }

    private static function arrayCache(): CacheInterface
    {
        return new class implements CacheInterface {
            /** @var array<string, string> */
            public array $items = [];

            public function get(string $key, mixed $default = null): mixed
            {
                return isset($this->items[$key]) ? unserialize($this->items[$key]) : $default;
            }

            public function set(string $key, mixed $value, null|int|\DateInterval $ttl = null): bool
            {
                $this->items[$key] = serialize($value);
                return true;
            }

            public function delete(string $key): bool
            {
                unset($this->items[$key]);
                return true;
            }

            public function clear(): bool
            {
                $this->items = [];
                return true;
            }

            /**
             * @param iterable<string> $keys
             * @return iterable<string, mixed>
             */
            public function getMultiple(iterable $keys, mixed $default = null): iterable
            {
                $values = [];
                foreach ($keys as $key) {
                    $values[$key] = $this->get($key, $default);
                }
                return $values;
            }

            /**
             * @param iterable<string, mixed> $values
             */
            public function setMultiple(iterable $values, null|int|\DateInterval $ttl = null): bool
            {
                foreach ($values as $key => $value) {
                    $this->set($key, $value);
                }
                return true;
            }

            /**
             * @param iterable<string> $keys
             */
            public function deleteMultiple(iterable $keys): bool
            {
                foreach ($keys as $key) {
                    $this->delete($key);
                }
                return true;
            }

            public function has(string $key): bool
            {
                return isset($this->items[$key]);
            }
        };
    }

    /**
     * @param Vector $vector
     */
    #[DataProvider('vectorCases')]
    public function testVector(array $vector): void
    {
        $run = fn () => WebhookVerifier::verifyWebhookSignature(
            $vector['body'],
            $vector['headers'],
            $vector['publicKeys'],
            now: $vector['now'],
        );

        if ($vector['valid']) {
            $run();
            $this->addToAssertionCount(1);
        } else {
            $this->expectException(WebhookVerificationError::class);
            $run();
        }
    }

    public function testAcceptsHeaderNamesInAnyCasingAndMultiValueHeaders(): void
    {
        $vector = self::vector('valid single signature');
        $headers = [];
        foreach ($vector['headers'] as $name => $value) {
            $headers[strtoupper($name)] = [$value, 'ignored'];
        }

        WebhookVerifier::verifyWebhookSignature($vector['body'], $headers, $vector['publicKeys'], now: $vector['now']);

        $this->addToAssertionCount(1);
    }

    public function testAcceptsAPsr7Message(): void
    {
        $vector = self::vector('valid single signature');
        $request = new ServerRequest('POST', '/webhooks/mobiscroll', $vector['headers'], $vector['body']);

        WebhookVerifier::verifyWebhookSignature(
            (string)$request->getBody(),
            $request,
            $vector['publicKeys'],
            now: $vector['now'],
        );

        $this->addToAssertionCount(1);
    }

    public function testReportsWhyVerificationFailed(): void
    {
        $stale = self::vector('timestamp 301 s old, stale');
        try {
            WebhookVerifier::verifyWebhookSignature($stale['body'], $stale['headers'], $stale['publicKeys'], now: $stale['now']);
            $this->fail('Expected WebhookVerificationError');
        } catch (WebhookVerificationError $e) {
            $this->assertInstanceOf(MobiscrollConnectException::class, $e);
            $this->assertSame(WebhookVerificationError::TIMESTAMP_OUT_OF_TOLERANCE, $e->getReason());
            $this->assertSame('WEBHOOK_VERIFICATION_ERROR', $e->getCodeString());
        }
    }

    public function testRejectsATimestampWithATrailingNewline(): void
    {
        $vector = self::vector('valid single signature');
        $headers = ['webhook-timestamp' => $vector['headers']['webhook-timestamp'] . "\n"] + $vector['headers'];

        $this->expectReason(
            WebhookVerificationError::INVALID_TIMESTAMP,
            fn () => WebhookVerifier::verifyWebhookSignature($vector['body'], $headers, $vector['publicKeys'], now: $vector['now']),
        );
    }

    public function testKeysUrlIsTheOriginOfTheBaseUrl(): void
    {
        $this->assertSame(self::KEYS_URL, WebhookKeyStore::keysUrl('https://connect.mobiscroll.com/api/'));
        $this->assertSame(
            'http://localhost:8080/.well-known/webhook-keys',
            WebhookKeyStore::keysUrl('http://localhost:8080/api/'),
        );
    }

    public function testFetchesTheKeysLazilyAndReturnsTheParsedDelivery(): void
    {
        $client = $this->createClient();
        $this->assertCount(0, $this->requests);

        $delivery = $this->verify($client);

        $this->assertCount(1, $this->requests);
        $this->assertSame('GET', $this->requests[0]['request']->getMethod());
        $this->assertSame(self::KEYS_URL, (string)$this->requests[0]['request']->getUri());
        $this->assertFalse($this->requests[0]['request']->hasHeader('Authorization'));
        $this->assertSame(10.0, $this->requests[0]['options']['timeout']);

        $this->assertSame('user_42', $delivery->userId);
        $this->assertSame('google', $delivery->provider);
        $this->assertSame('primary', $delivery->calendarId);
        $this->assertInstanceOf(WebhookEvent::class, $delivery->events[0]);
        $this->assertSame('Café meeting ☕ — Zoë', $delivery->events[0]->title);
        $this->assertSame('evt_1', $delivery->events[0]->id);
        $this->assertSame('google', $delivery->events[0]->provider);
        $this->assertSame(1, $delivery->metadata->eventCount);
    }

    public function testSharesOneKeyCacheAcrossClientInstancesAndHonoursMaxAge(): void
    {
        $this->respond = fn (): ResponseInterface => self::keysResponse([self::key('active')], 'max-age=120');

        $this->verify($this->createClient());
        $this->verify($this->createClient());
        $this->assertCount(1, $this->requests);

        $this->clock += 121;
        $this->verify($this->createClient());
        $this->assertCount(2, $this->requests);
    }

    public function testRefetchesOnceAfterARotationAndAcceptsTheDelivery(): void
    {
        $responses = [self::keysResponse([self::key('unrelated')]), self::keysResponse([self::key('active')])];
        $this->respond = function () use (&$responses): ResponseInterface {
            return array_shift($responses) ?? throw new \RuntimeException('Unexpected fetch');
        };
        $this->clock -= 61;
        WebhookKeyStore::forUrl(self::KEYS_URL)->getKeys();
        $this->clock += 61;

        $delivery = $this->verify($this->createClient());

        $this->assertSame('primary', $delivery->calendarId);
        $this->assertCount(2, $this->requests);
    }

    public function testDoesNotRefetchMoreThanOnceAMinuteOnForgedDeliveries(): void
    {
        $client = $this->createClient();

        $this->expectReason(WebhookVerificationError::NO_MATCHING_SIGNATURE, fn () => $this->verify($client, 'tampered body'));
        $this->expectReason(WebhookVerificationError::NO_MATCHING_SIGNATURE, fn () => $this->verify($client, 'tampered body'));

        $this->assertCount(1, $this->requests);
    }

    public function testFallsBackToThePinnedKeyWhenTheEndpointCannotBeReached(): void
    {
        $this->respond = fn (RequestInterface $request): \Throwable => new ConnectException('ECONNREFUSED', $request);

        $delivery = $this->verify($this->createClient(self::key('active')));

        $this->assertSame('google', $delivery->provider);
    }

    public function testFallsBackToThePinnedKeyWhenTheEndpointFails(): void
    {
        $this->respond = fn (): ResponseInterface => new Response(503, [], 'unavailable');

        $delivery = $this->verify($this->createClient(self::key('active')));

        $this->assertSame('user_42', $delivery->userId);
    }

    public function testIgnoresThePinnedKeyWhileFetchedKeysAreAvailable(): void
    {
        $this->respond = fn (): ResponseInterface => self::keysResponse([self::key('unrelated')]);

        $this->expectReason(
            WebhookVerificationError::NO_MATCHING_SIGNATURE,
            fn () => $this->verify($this->createClient(self::key('active'))),
        );
    }

    public function testKeepsTheLastGoodKeysWhenARefreshFails(): void
    {
        $client = $this->createClient();
        $this->verify($client);

        $this->respond = fn (RequestInterface $request): \Throwable => new ConnectException('timeout', $request);
        $this->clock += 3601;
        $vector = self::vector('valid single signature');
        $later = ['webhook-timestamp' => (string)($vector['now'] + 3601)] + $vector['headers'];

        $this->expectReason(
            WebhookVerificationError::NO_MATCHING_SIGNATURE,
            fn () => $client->webhooks()->verifyWebhook($vector['body'], $later),
        );
        $this->assertCount(2, $this->requests);
        $this->assertSame([self::key('active')], WebhookKeyStore::forUrl(self::KEYS_URL)->getKeys());
        $this->assertCount(2, $this->requests);
    }

    public function testIgnoresKeysWithAnotherAlgorithmOrPrefix(): void
    {
        $this->respond = fn (): ResponseInterface => new Response(200, [], (string)json_encode(['keys' => [
            ['id' => 'a', 'alg' => 'rsa', 'key' => self::key('active')],
            ['id' => 'b', 'key' => 'pk_' . self::key('active')],
            ['id' => 'c', 'alg' => 'Ed25519', 'key' => self::key('previous')],
        ]]));

        $this->assertSame([self::key('previous')], WebhookKeyStore::forUrl(self::KEYS_URL)->getKeys());
    }

    public function testFailsWithNoPublicKeysWhenNothingCanBeFetchedAndNoKeyIsPinned(): void
    {
        $this->respond = fn (RequestInterface $request): \Throwable => new ConnectException('ECONNREFUSED', $request);

        $this->expectReason(WebhookVerificationError::NO_PUBLIC_KEYS, fn () => $this->verify($this->createClient()));
        $this->assertCount(1, $this->requests);
    }

    public function testDoesNotFetchForFailuresThatNewKeysCannotFix(): void
    {
        $this->clock = (float)self::vector('timestamp 301 s in the future')['now'];

        $this->expectReason(
            WebhookVerificationError::TIMESTAMP_OUT_OF_TOLERANCE,
            fn () => $this->verify($this->createClient(), 'timestamp 301 s in the future'),
        );
        $this->assertCount(1, $this->requests);
    }

    public function testPsr16CacheSharesKeysAcrossRequests(): void
    {
        $cache = self::arrayCache();
        $this->verify($this->createClient(cache: $cache));
        $this->assertCount(1, $this->requests);

        $this->startRequest();
        $this->verify($this->createClient(cache: $cache));
        $this->assertCount(1, $this->requests);

        $this->startRequest();
        $this->clock += 3601;
        $vector = self::vector('valid single signature');
        $this->expectReason(
            WebhookVerificationError::NO_MATCHING_SIGNATURE,
            fn () => $this->createClient(cache: $cache)->webhooks()->verifyWebhook(
                $vector['body'],
                ['webhook-timestamp' => (string)($vector['now'] + 3601)] + $vector['headers'],
            ),
        );
        $this->assertCount(2, $this->requests);
    }

    public function testPsr16CacheSharesTheFetchAttemptAcrossRequests(): void
    {
        $cache = self::arrayCache();
        $this->respond = fn (RequestInterface $request): \Throwable => new ConnectException('ECONNREFUSED', $request);

        $this->expectReason(WebhookVerificationError::NO_PUBLIC_KEYS, fn () => $this->verify($this->createClient(cache: $cache)));
        $this->startRequest();
        $this->expectReason(WebhookVerificationError::NO_PUBLIC_KEYS, fn () => $this->verify($this->createClient(cache: $cache)));
        $this->assertCount(1, $this->requests);

        $this->startRequest();
        $this->clock += 60;
        $this->respond = fn (): ResponseInterface => self::keysResponse([self::key('active')]);
        $this->verify($this->createClient(cache: $cache));
        $this->assertCount(2, $this->requests);
    }

    public function testPsr16CachePicksUpKeysAnotherWorkerFetchedAfterAMismatch(): void
    {
        $cache = self::arrayCache();
        $this->respond = fn (): ResponseInterface => self::keysResponse([self::key('unrelated')]);
        $this->clock -= 61;
        $staleWorker = WebhookKeyStore::forUrl(self::KEYS_URL);
        $staleWorker->getKeys($cache);

        $this->startRequest();
        $this->clock += 61;
        $this->respond = fn (): ResponseInterface => self::keysResponse([self::key('active')]);
        $worker = WebhookKeyStore::forUrl(self::KEYS_URL);
        $this->assertSame([self::key('unrelated')], $worker->getKeys($cache));
        $this->assertTrue($worker->refreshAfterMismatch($cache));
        $this->assertCount(2, $this->requests);

        $this->assertTrue($staleWorker->refreshAfterMismatch($cache));
        $this->assertSame([self::key('active')], $staleWorker->getKeys($cache));
        $this->assertCount(2, $this->requests);
    }
}
