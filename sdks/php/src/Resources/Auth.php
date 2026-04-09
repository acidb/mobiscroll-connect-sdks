<?php

declare(strict_types=1);

namespace Mobiscroll\Connect\Resources;

use Mobiscroll\Connect\Exceptions\MobiscrollConnectException;
use Mobiscroll\Connect\{ApiClient, DisconnectResponse, TokenResponse, ConnectionStatusResponse};

class Auth
{
    public function __construct(private ApiClient $apiClient)
    {
    }

    /**
     * Generate an OAuth2 authorization URL
     *
     * @param string $userId Required: User ID to associate with this authorization
     * @param string $scope Optional: Requested OAuth scopes (default: 'calendar')
     * @param string|null $state Optional: CSRF protection state value
     * @param string|null $providers Optional: Comma-separated list of providers to authorize
     * @return string The complete OAuth2 authorization URL
     */
    public function generateAuthUrl(
        string $userId,
        string $scope = 'calendar',
        ?string $state = null,
        ?string $providers = null
    ): string {
        $config = $this->apiClient->getConfig();

        $params = [
            'client_id' => $config->clientId,
            'response_type' => 'code',
            'user_id' => $userId,
            'redirect_uri' => $config->redirectUri,
            'scope' => $scope,
        ];

        if ($state !== null) {
            $params['state'] = $state;
        }

        if ($providers !== null) {
            $params['providers'] = $providers;
        }

        return 'https://connect.mobiscroll.com/api/oauth/authorize?' . http_build_query($params);
    }

    /**
     * Exchange an authorization code for access tokens
     * Uses HTTP Basic authentication with clientId:clientSecret
     *
     * @param string $code Authorization code from OAuth callback
     * @return TokenResponse Parsed token response
     */
    public function getToken(string $code): TokenResponse
    {
        $config = $this->apiClient->getConfig();

        // Build form-urlencoded body
        $body = http_build_query([
            'grant_type' => 'authorization_code',
            'code' => $code,
            'redirect_uri' => $config->redirectUri,
        ]);

        // Basic auth header: base64(clientId:clientSecret)
        $credentials = base64_encode($config->clientId . ':' . $config->clientSecret);

        // POST with custom headers for Basic auth and form content-type
        // Note: postRaw calls go through ApiClient which adds /api base_uri
        $response = $this->apiClient->postRaw('/oauth/token', $body, [
            'Content-Type' => 'application/x-www-form-urlencoded',
            'Authorization' => "Basic {$credentials}",
        ]);

        /** @var array<string, mixed> $data */
        $data = is_array($response) ? $response : [];
        return TokenResponse::fromArray($data);
    }

    public function setCredentials(TokenResponse $tokens): void
    {
        $this->apiClient->setCredentials($tokens);
    }

    public function getConnectionStatus(): ConnectionStatusResponse
    {
        try {
            $response = $this->apiClient->get('/oauth/connection-status');
        } catch (MobiscrollConnectException $firstError) {
            // Some deployments expose legacy routes without the /oauth prefix.
            $response = $this->apiClient->get('/connection-status');
        }

        /** @var array<string, mixed> $data */
        $data = is_array($response) ? $response : [];
        return ConnectionStatusResponse::fromArray($data);
    }

    public function disconnect(string $provider, ?string $account = null): DisconnectResponse
    {
        $query = ['provider' => $provider];
        if ($account !== null && $account !== '') {
            $query['account'] = $account;
        }

        try {
            $path = '/oauth/disconnect?' . http_build_query($query);
            $response = $this->apiClient->post($path, []);
        } catch (MobiscrollConnectException $firstError) {
            // Backward-compatible fallback for environments that still use legacy routes.
            $path = '/disconnect?' . http_build_query($query);
            $response = $this->apiClient->post($path, []);
        }

        /** @var array<string, mixed> $data */
        $data = is_array($response) ? $response : [];
        return DisconnectResponse::fromArray($data);
    }
}
