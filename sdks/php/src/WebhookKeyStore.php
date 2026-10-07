<?php

declare(strict_types=1);

namespace Mobiscroll\Connect;

use GuzzleHttp\Client as GuzzleClient;
use GuzzleHttp\ClientInterface;
use GuzzleHttp\Exception\GuzzleException;
use Psr\SimpleCache\CacheInterface;

/**
 * Process-wide cache of the webhook public keys published at one keys URL, shared by every client
 * instance. Follows the endpoint's `Cache-Control`, attempts a fetch at most once a minute, and keeps
 * the last good keys when a fetch fails.
 *
 * Under PHP-FPM the process-wide state lives for one request only; pass a PSR-16 cache to share the
 * keys and the fetch timestamps across requests and workers.
 *
 * @internal
 */
final class WebhookKeyStore
{
    private const DEFAULT_MAX_AGE_SECONDS = 3600;
    private const MAX_MAX_AGE_SECONDS = 86400;
    private const MIN_REFETCH_INTERVAL_SECONDS = 60;
    private const FETCH_TIMEOUT_SECONDS = 10.0;
    private const CACHE_KEY_PREFIX = 'mobiscroll_connect_whk_';

    /** @var array<string, self> */
    private static array $stores = [];
    private static ?ClientInterface $httpClient = null;
    /** @var (\Closure(): float)|null */
    private static ?\Closure $clock = null;

    /** @var array<int, string> */
    private array $keys = [];
    private float $fetchedAt = 0.0;
    private float $lastAttemptAt = 0.0;
    private int $maxAge = self::DEFAULT_MAX_AGE_SECONDS;

    private function __construct(private readonly string $url)
    {
    }

    public static function forUrl(string $url): self
    {
        return self::$stores[$url] ??= new self($url);
    }

    /**
     * The keys URL for an API base URL: its origin plus `/.well-known/webhook-keys`.
     */
    public static function keysUrl(string $baseUri): string
    {
        $parts = parse_url($baseUri);
        $origin = ($parts['scheme'] ?? 'https') . '://' . ($parts['host'] ?? '')
            . (isset($parts['port']) ? ':' . $parts['port'] : '');
        return $origin . WebhookVerifier::KEYS_PATH;
    }

    /**
     * Clears every cached key set and restores the default HTTP client and clock; for tests.
     */
    public static function reset(): void
    {
        self::$stores = [];
        self::$httpClient = null;
        self::$clock = null;
    }

    /**
     * Current Unix time in seconds.
     */
    public static function now(): float
    {
        return self::$clock !== null ? (self::$clock)() : microtime(true);
    }

    /**
     * The cached keys, fetching them first when they are missing or older than the endpoint's max-age
     * and the once-a-minute limit allows.
     *
     * @return array<int, string>
     */
    public function getKeys(?CacheInterface $cache = null): array
    {
        if ($this->isStale()) {
            $this->load($cache);
            if ($this->isStale() && $this->canRefetch()) {
                $this->refresh($cache);
            }
        }
        return $this->keys;
    }

    /**
     * Called after a delivery matched none of the cached keys. Picks up keys another worker stored in
     * the shared cache, or fetches them again if the once-a-minute limit allows.
     *
     * @return bool Whether the keys may have changed, so verifying again is worthwhile
     */
    public function refreshAfterMismatch(?CacheInterface $cache = null): bool
    {
        $fetchedAt = $this->fetchedAt;
        $this->load($cache);
        if ($this->fetchedAt > $fetchedAt) {
            return true;
        }
        if (!$this->canRefetch()) {
            return false;
        }
        $this->refresh($cache);
        return true;
    }

    private function isStale(): bool
    {
        return $this->keys === [] || self::now() - $this->fetchedAt > $this->maxAge;
    }

    private function canRefetch(): bool
    {
        return self::now() - $this->lastAttemptAt >= self::MIN_REFETCH_INTERVAL_SECONDS;
    }

    private function refresh(?CacheInterface $cache): void
    {
        $this->lastAttemptAt = self::now();
        $this->save($cache);

        try {
            $response = self::httpClient()->request('GET', $this->url, [
                'timeout' => self::FETCH_TIMEOUT_SECONDS,
                'headers' => ['Accept' => 'application/json'],
            ]);
        } catch (GuzzleException) {
            return;
        }

        $data = json_decode((string)$response->getBody(), true);
        $keys = [];
        foreach (is_array($data) && is_array($data['keys'] ?? null) ? $data['keys'] : [] as $entry) {
            $key = is_array($entry) ? ($entry['key'] ?? null) : null;
            $alg = is_array($entry) ? ($entry['alg'] ?? null) : null;
            if (
                is_string($key)
                && str_starts_with($key, WebhookVerifier::PUBLIC_KEY_PREFIX)
                && ($alg === null || $alg === '' || (is_string($alg) && strtolower($alg) === 'ed25519'))
            ) {
                $keys[] = $key;
            }
        }
        if ($keys === []) {
            return;
        }

        $this->keys = $keys;
        $this->fetchedAt = self::now();
        $this->maxAge = self::parseMaxAge($response->getHeaderLine('Cache-Control'));
        $this->save($cache);
    }

    private static function parseMaxAge(string $cacheControl): int
    {
        if (preg_match('/max-age=(\d+)/i', $cacheControl, $match) !== 1) {
            return self::DEFAULT_MAX_AGE_SECONDS;
        }
        return (int)min((float)$match[1], self::MAX_MAX_AGE_SECONDS);
    }

    private static function httpClient(): ClientInterface
    {
        return self::$httpClient ??= new GuzzleClient();
    }

    private function cacheKey(): string
    {
        return self::CACHE_KEY_PREFIX . sha1($this->url);
    }

    /**
     * Merges the state another worker stored in the shared cache: its last fetch attempt always, its
     * keys when they are newer than ours. Cache failures are ignored; the cache is an optimisation.
     */
    private function load(?CacheInterface $cache): void
    {
        if ($cache === null) {
            return;
        }
        try {
            $state = $cache->get($this->cacheKey());
        } catch (\Exception) {
            return;
        }
        if (!is_array($state)) {
            return;
        }

        $lastAttemptAt = $state['lastAttemptAt'] ?? null;
        if (is_float($lastAttemptAt) || is_int($lastAttemptAt)) {
            $this->lastAttemptAt = max($this->lastAttemptAt, (float)$lastAttemptAt);
        }

        $keys = $state['keys'] ?? null;
        $fetchedAt = $state['fetchedAt'] ?? null;
        $maxAge = $state['maxAge'] ?? null;
        if (
            is_array($keys) && $keys !== [] && array_is_list($keys)
            && array_filter($keys, 'is_string') === $keys
            && (is_float($fetchedAt) || is_int($fetchedAt)) && $fetchedAt > $this->fetchedAt
            && is_int($maxAge)
        ) {
            /** @var array<int, string> $keys */
            $this->keys = $keys;
            $this->fetchedAt = (float)$fetchedAt;
            $this->maxAge = min($maxAge, self::MAX_MAX_AGE_SECONDS);
        }
    }

    private function save(?CacheInterface $cache): void
    {
        if ($cache === null) {
            return;
        }
        try {
            $cache->set($this->cacheKey(), [
                'keys' => $this->keys,
                'fetchedAt' => $this->fetchedAt,
                'maxAge' => $this->maxAge,
                'lastAttemptAt' => $this->lastAttemptAt,
            ]);
        } catch (\Exception) {
            return;
        }
    }
}
