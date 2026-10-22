<?php

declare(strict_types=1);

namespace Mobiscroll\Connect\Resources;

use Mobiscroll\Connect\Exceptions\{MobiscrollConnectException, ValidationError};
use Mobiscroll\Connect\{ApiClient, DisconnectResponse, RevokeResponse, TokenResponse, ConnectionStatusResponse};

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
     * @param string|null $lng Optional: Language code for the Connect pages, e.g. 'es'.
     *                         Supported languages: https://mobiscroll.com/docs/connect/localization#supported-languages
     * @return string The complete OAuth2 authorization URL
     */
    public function generateAuthUrl(
        string $userId,
        string $scope = 'calendar',
        ?string $state = null,
        ?string $providers = null,
        ?string $lng = null
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

        if ($lng !== null) {
            $params['lng'] = $lng;
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

    /**
     * Disconnect a calendar provider account
     *
     * Removes the account (or, without $account, every account of the provider) from the
     * user: stops its webhook subscriptions and deletes the stored provider credentials.
     * It does not revoke the user's Connect access or refresh tokens (see revoke()), nor
     * the grant at Google or Microsoft.
     *
     * Example:
     *   $client->auth()->disconnect('google', 'user@gmail.com'); // one account
     *   $client->auth()->disconnect('microsoft');                // every Microsoft account
     *
     * @param string $provider 'google', 'microsoft', 'apple' or 'caldav'
     * @param string|null $account The `id` of an account from getConnectionStatus()
     *                             (`connections[provider][].id`); null disconnects every account of the provider
     * @return DisconnectResponse Success confirmation
     */
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

    /**
     * Revoke the user's Connect access and refresh tokens
     *
     * Revokes the whole grant: every access and refresh token issued for this user. It does
     * not disconnect calendar accounts or stop webhook subscriptions. To remove a user
     * entirely, call disconnect() for each provider first (it needs a valid access token),
     * then revoke().
     *
     * Example:
     *   $client->auth()->disconnect('google');
     *   $client->auth()->revoke();
     *
     *   // Or revoke a token you stored yourself
     *   $client->auth()->revoke($storedRefreshToken);
     *
     * @param string|null $token Access or refresh token to revoke. Defaults to the stored refresh
     *                           token, else the stored access token; the stored credentials are then cleared.
     * @return RevokeResponse success is true also for an unknown or already revoked token
     * @throws ValidationError When no token is given and none is stored
     */
    public function revoke(?string $token = null): RevokeResponse
    {
        $revokeToken = $token;
        if ($revokeToken === null) {
            $credentials = $this->apiClient->getCredentials() ?? [];
            $revokeToken = !empty($credentials['refresh_token'])
                ? $credentials['refresh_token']
                : ($credentials['access_token'] ?? null);
        }

        if (!is_string($revokeToken) || $revokeToken === '') {
            throw new ValidationError('No token to revoke: pass one or set credentials first');
        }

        // postRaw never refreshes on 401, so revoking can never mint new tokens.
        $response = $this->apiClient->postRaw(
            '/oauth/revoke',
            (string)json_encode(['token' => $revokeToken]),
            ['Content-Type' => 'application/json'],
        );

        if ($token === null) {
            $this->apiClient->clearCredentials();
        }

        /** @var array<string, mixed> $data */
        $data = is_array($response) ? $response : [];
        return RevokeResponse::fromArray($data);
    }
}
