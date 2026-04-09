<?php

declare(strict_types=1);

namespace Mobiscroll\Connect;

use GuzzleHttp\Client as GuzzleClient;
use GuzzleHttp\Exception\GuzzleException;
use GuzzleHttp\Exception\RequestException;
use Mobiscroll\Connect\Exceptions\{
    AuthenticationError,
    MobiscrollConnectException,
    NetworkError,
    NotFoundError,
    RateLimitError,
    ServerError,
    ValidationError,
};
use Psr\Http\Message\ResponseInterface;

class ApiClient
{
    /** @var array<string, mixed>|null */
    private ?array $credentials = null;
    private GuzzleClient $httpClient;

    public function __construct(private Config $config)
    {
        if (empty($config->clientId) || empty($config->clientSecret) || empty($config->redirectUri)) {
            throw new MobiscrollConnectException(
                'Client ID, Client Secret and Redirect URI are required'
            );
        }

        $this->httpClient = new GuzzleClient([
            // Trailing slash is required so relative request paths resolve under /api/
            'base_uri' => 'https://connect.mobiscroll.com/api/',
            'timeout' => 30.0,
            'headers' => [
                'Content-Type' => 'application/json',
            ],
        ]);
    }

    public function setCredentials(TokenResponse $tokens): void
    {
        $this->credentials = $tokens->toArray();
    }

    /**
     * @return array<string, mixed>|null
     */
    public function getCredentials(): ?array
    {
        return $this->credentials;
    }

    public function getConfig(): Config
    {
        return $this->config;
    }

    /**
     * GET request
     * @param array<string, string|null>|null $query
     * @return array<string, mixed>|object|null
     */
    public function get(string $path, ?array $query = null, string $responseClass = 'array'): mixed
    {
        $path = $this->normalizePath($path);
        $options = ['headers' => $this->getAuthHeaders()];
        if ($query !== null) {
            $options['query'] = $query;
        }

        try {
            $response = $this->httpClient->get($path, $options);
            return $this->parseResponse($response, $responseClass);
        } catch (RequestException $e) {
            throw $this->handleRequestException($e);
        } catch (GuzzleException $e) {
            throw new NetworkError($e->getMessage());
        }
    }

    /**
     * POST request
     * @return array<string, mixed>|object|null
     */
    public function post(string $path, mixed $data = null, string $responseClass = 'array'): mixed
    {
        $path = $this->normalizePath($path);
        $options = ['headers' => $this->getAuthHeaders()];

        if ($data !== null) {
            if (is_array($data)) {
                $options['json'] = $data;
            } elseif (is_string($data)) {
                $options['body'] = $data;
                $options['headers']['Content-Type'] = 'application/x-www-form-urlencoded';
            }
        }

        try {
            $response = $this->httpClient->post($path, $options);
            return $this->parseResponse($response, $responseClass);
        } catch (RequestException $e) {
            throw $this->handleRequestException($e);
        } catch (GuzzleException $e) {
            throw new NetworkError($e->getMessage());
        }
    }

    /**
     * POST request with custom headers (for OAuth token exchange with Basic auth)
     * @param array<string, string> $headers
     * @return array<string, mixed>|object|null
     */
    public function postRaw(string $path, string $body, array $headers = [], string $responseClass = 'array'): mixed
    {
        $path = $this->normalizePath($path);
        $mergedHeaders = array_merge($this->getAuthHeaders(), $headers);

        $options = [
            'headers' => $mergedHeaders,
            'body' => $body,
        ];

        try {
            $response = $this->httpClient->post($path, $options);
            return $this->parseResponse($response, $responseClass);
        } catch (RequestException $e) {
            throw $this->handleRequestException($e);
        } catch (GuzzleException $e) {
            throw new NetworkError($e->getMessage());
        }
    }

    /**
     * PUT request
     * @return array<string, mixed>|object|null
     */
    public function put(string $path, mixed $data = null, string $responseClass = 'array'): mixed
    {
        $path = $this->normalizePath($path);
        $options = ['headers' => $this->getAuthHeaders()];

        if ($data !== null) {
            if (is_array($data)) {
                $options['json'] = $data;
            }
        }

        try {
            $response = $this->httpClient->put($path, $options);
            return $this->parseResponse($response, $responseClass);
        } catch (RequestException $e) {
            throw $this->handleRequestException($e);
        } catch (GuzzleException $e) {
            throw new NetworkError($e->getMessage());
        }
    }

    /**
     * DELETE request
     * @param array<string, string|null>|null $query
     * @return array<string, mixed>|object|null
     */
    public function delete(string $path, mixed $data = null, ?array $query = null, string $responseClass = 'array'): mixed
    {
        $path = $this->normalizePath($path);
        $options = ['headers' => $this->getAuthHeaders()];

        if ($data !== null) {
            $options['json'] = $data;
        }

        if ($query !== null) {
            $options['query'] = $query;
        }

        try {
            $response = $this->httpClient->delete($path, $options);
            return $this->parseResponse($response, $responseClass);
        } catch (RequestException $e) {
            throw $this->handleRequestException($e);
        } catch (GuzzleException $e) {
            throw new NetworkError($e->getMessage());
        }
    }

    /**
     * Parse HTTP response to appropriate return type
     * @return array<string, mixed>|object|null
     */
    private function parseResponse(ResponseInterface $response, string $responseClass): mixed
    {
        $body = (string)$response->getBody();

        if ($response->getStatusCode() === 204) {
            return null;
        }

        $data = json_decode($body, true);

        if ($responseClass === 'array') {
            return $data ?? [];
        }

        if (class_exists($responseClass)) {
            return new $responseClass(...(array)$data);
        }

        return $data;
    }

    /**
     * @return array<string, string>
     */
    private function getAuthHeaders(): array
    {
        $headers = [];

        if ($this->credentials && !empty($this->credentials['access_token'])) {
            $headers['Authorization'] = 'Bearer ' . $this->credentials['access_token'];
        }

        return $headers;
    }

    /**
     * Keep request paths relative so Guzzle preserves /api/ from base_uri.
     */
    private function normalizePath(string $path): string
    {
        return ltrim($path, '/');
    }

    private function handleRequestException(RequestException $e): MobiscrollConnectException
    {
        $response = $e->getResponse();

        if (!$response) {
            return new NetworkError($e->getMessage());
        }

        $status = $response->getStatusCode();
        $body = (string)$response->getBody();
        $data = json_decode($body, true) ?? [];
        $message = $data['message'] ?? $e->getMessage();

        return match ($status) {
            401, 403 => new AuthenticationError($message),
            404 => new NotFoundError($message),
            400, 422 => new ValidationError($message, $data['details'] ?? []),
            429 => new RateLimitError(
                $message,
                isset($response->getHeader('retry-after')[0])
                    ? (int)$response->getHeader('retry-after')[0]
                    : null
            ),
            500, 502, 503, 504 => new ServerError($message, $status),
            default => new MobiscrollConnectException(
                $message,
                $data['code'] ?? 'UNKNOWN_ERROR'
            ),
        };
    }
}
