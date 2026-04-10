<?php

declare(strict_types=1);

namespace Mobiscroll\Connect\Tests\Smoke;

use PHPUnit\Framework\TestCase;

class MinimalAppSmokeTest extends TestCase
{
    /** @var resource|null */
    private static $serverProcess = null;

    private static string $baseUrl = '';
    private static ?string $skipReason = null;

    public static function setUpBeforeClass(): void
    {
        $repoRoot = dirname(__DIR__, 2);
        $minimalAppRoot = $repoRoot . '/minimal-app';
        $documentRoot = $minimalAppRoot . '/public';
        $minimalAppAutoload = $minimalAppRoot . '/vendor/autoload.php';

        if (!is_file($minimalAppAutoload)) {
            self::$skipReason = 'minimal-app dependencies are missing. Run "cd minimal-app && composer install" first.';
            return;
        }

        $port = self::findAvailablePort(18080, 18120);
        self::$baseUrl = "http://127.0.0.1:{$port}";

        $phpBinary = PHP_BINARY;
        $command = escapeshellarg($phpBinary)
            . ' -S 127.0.0.1:' . $port
            . ' -t ' . escapeshellarg($documentRoot);

        $logFile = $repoRoot . '/.smoke-test-server.log';
        $descriptors = [
            0 => ['pipe', 'r'],
            1 => ['file', $logFile, 'a'],
            2 => ['file', $logFile, 'a'],
        ];

        $env = array_merge($_ENV, [
            'MOBISCROLL_CLIENT_ID' => 'smoke-client-id',
            'MOBISCROLL_CLIENT_SECRET' => 'smoke-client-secret',
            'MOBISCROLL_REDIRECT_URI' => self::$baseUrl . '/?action=callback',
            'MOBISCROLL_USER_ID' => 'smoke-user-id',
            'MOBISCROLL_PROVIDER' => 'google',
        ]);

        $process = proc_open($command, $descriptors, $pipes, $minimalAppRoot, $env);

        if (!is_resource($process)) {
            self::$skipReason = 'failed to start PHP built-in server for smoke tests.';
            return;
        }

        self::$serverProcess = $process;

        $isReady = false;
        for ($i = 0; $i < 50; $i++) {
            $result = self::request('/?action=session');
            if ($result['status'] > 0) {
                $isReady = true;
                break;
            }
            usleep(100000);
        }

        if (!$isReady) {
            self::$skipReason = 'smoke test server did not become ready in time.';
            self::shutdownServer();
        }
    }

    public static function tearDownAfterClass(): void
    {
        self::shutdownServer();
    }

    public function testAuthUrlEndpointReturnsExpectedPayload(): void
    {
        $this->skipIfNeeded();

        $result = self::request('/?action=auth-url');

        $this->assertSame(200, $result['status']);
        $this->assertTrue($result['json']['ok'] ?? false);
        $this->assertSame('auth-url', $result['json']['action'] ?? null);
        $this->assertStringContainsString('/api/oauth/authorize', (string)($result['json']['authUrl'] ?? ''));
    }

    public function testProtectedEndpointRequiresToken(): void
    {
        $this->skipIfNeeded();

        $result = self::request('/?action=calendars');

        $this->assertSame(401, $result['status']);
        $this->assertFalse($result['json']['ok'] ?? true);
        $this->assertStringContainsString('No stored token', (string)($result['json']['error'] ?? ''));
    }

    public function testUnknownActionReturnsSupportedActions(): void
    {
        $this->skipIfNeeded();

        $result = self::request('/?action=unknown-action');

        $this->assertSame(400, $result['status']);
        $this->assertFalse($result['json']['ok'] ?? true);
        $this->assertIsArray($result['json']['supportedActions'] ?? null);
        $this->assertContains('auth-url', $result['json']['supportedActions']);
    }

    public function testClearSessionEndpointWorks(): void
    {
        $this->skipIfNeeded();

        $result = self::request('/?action=clear-session');

        $this->assertSame(200, $result['status']);
        $this->assertTrue($result['json']['ok'] ?? false);
        $this->assertSame('clear-session', $result['json']['action'] ?? null);
    }

    private function skipIfNeeded(): void
    {
        if (self::$skipReason !== null) {
            $this->markTestSkipped(self::$skipReason);
        }
    }

    private static function findAvailablePort(int $from, int $to): int
    {
        for ($port = $from; $port <= $to; $port++) {
            $socket = @stream_socket_server("tcp://127.0.0.1:{$port}", $errno, $errstr);
            if ($socket !== false) {
                fclose($socket);
                return $port;
            }
        }

        throw new \RuntimeException('no available port found for smoke test server.');
    }

    /**
     * @return array{status:int, json:array<string,mixed>, body:string}
     */
    private static function request(string $path): array
    {
        $url = self::$baseUrl . $path;
        $context = stream_context_create([
            'http' => [
                'method' => 'GET',
                'ignore_errors' => true,
                'timeout' => 3,
            ],
        ]);

        $body = @file_get_contents($url, false, $context);
        $body = is_string($body) ? $body : '';

        $headers = $http_response_header ?? [];
        $status = 0;
        if (isset($headers[0]) && preg_match('/\s(\d{3})\s/', $headers[0], $matches) === 1) {
            $status = (int)$matches[1];
        }

        $decoded = json_decode($body, true);
        $json = is_array($decoded) ? $decoded : [];

        return [
            'status' => $status,
            'json' => $json,
            'body' => $body,
        ];
    }

    private static function shutdownServer(): void
    {
        if (is_resource(self::$serverProcess)) {
            proc_terminate(self::$serverProcess);
            proc_close(self::$serverProcess);
            self::$serverProcess = null;
        }
    }
}
