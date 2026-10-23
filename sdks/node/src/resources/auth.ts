import { ApiClient } from '../client';
import {
  AuthorizeParams,
  TokenResponse,
  ConnectionStatusResponse,
  DisconnectParams,
  DisconnectResponse,
  RevokeResponse,
  ValidationError,
} from '../types';

/**
 * Auth resource for managing OAuth2 authentication and provider connections
 */
export class Auth {
  constructor(private readonly client: ApiClient) {}

  /**
   * Get the OAuth2 authorization URL to initiate the authorization flow
   *
   * @param params - Authorization parameters
   * @returns The complete authorization URL to redirect the user to
   *
   * @example
   * ```typescript
   * const authUrl = client.auth.generateAuthUrl({
   *   userId: 'user-123',
   *   state: 'optional-state-value',
   * });
   *
   * // Redirect user to authUrl
   * window.location.href = authUrl;
   * ```
   */
  generateAuthUrl(params: AuthorizeParams): string {
    const config = this.client.getConfig();
    const { clientId, redirectUri } = config;
    const { userId, state, scope, providers, lng, theme } = params;

    const queryParams = new URLSearchParams({
      response_type: 'code',
      client_id: clientId,
      user_id: userId,
      redirect_uri: redirectUri,
    });

    if (state) queryParams.set('state', state);
    if (scope) queryParams.set('scope', scope);
    if (providers) queryParams.set('providers', providers);
    if (lng) queryParams.set('lng', lng);
    if (theme) queryParams.set('theme', theme);

    const baseURL = this.client.baseURL;
    return `${baseURL}/oauth/authorize?${queryParams.toString()}`;
  }

  /**
   * Exchange an authorization code for an access token
   *
   * @param code - Authorization code from the OAuth callback
   * @returns Access token and related information
   *
   * @example
   * ```typescript
   * // In your callback handler:
   * const { access_token } = await client.auth.getToken(authorizationCode);
   * ```
   */
  async getToken(code: string): Promise<TokenResponse> {
    const { clientId, clientSecret, redirectUri } = this.client.getConfig();

    const body = new URLSearchParams({
      grant_type: 'authorization_code',
      code: code,
      redirect_uri: redirectUri,
    });

    const credentials = Buffer.from(`${clientId}:${clientSecret}`).toString('base64');

    try {
      const response = await this.client.post<TokenResponse>('/oauth/token', body, {
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          Authorization: `Basic ${credentials}`,
          CLIENT_ID: clientId,
        },
      });

      this.setCredentials(response.data);
      return response.data;
    } catch {
      throw new Error(
        'Error exchanging code for token. Please check your client ID, client secret, and redirect URI configuration and try again.'
      );
    }
  }

  /**
   * Set the credentials (access token) for the client
   * @param tokens - The tokens to set
   */
  setCredentials(tokens: TokenResponse): void {
    this.client.setCredentials(tokens);
  }

  /**
   * Get the connection status for all calendar providers
   *
   * Returns which provider accounts are currently connected for the authenticated user.
   *
   * @returns Connection status for all providers
   *
   * @example
   * ```typescript
   * const status = await client.auth.getConnectionStatus();
   *
   * console.log(status.connections.google); // Array of connected Google accounts
   * console.log(status.connections.microsoft); // Array of connected Microsoft accounts
   * console.log(status.limitReached); // Whether account limit is reached
   *
   * // Accounts that connected but withheld calendar access on the provider's consent
   * // screen. They can list no calendars until the user reconnects and allows it.
   * const needsCalendarAccess = status.connections.google.filter(
   *   (account) => account.calendarPermissionGranted === false,
   * );
   * ```
   */
  async getConnectionStatus(): Promise<ConnectionStatusResponse> {
    const response = await this.client.get<ConnectionStatusResponse>('/oauth/connection-status');
    return response.data;
  }

  /**
   * Disconnect a calendar provider account
   *
   * Removes the account (or, without `account`, every account of the provider) from the
   * user: stops its webhook subscriptions and deletes the stored provider credentials.
   * It does not revoke the user's Connect access or refresh tokens (see `revoke()`), nor
   * the grant at Google or Microsoft.
   *
   * @param params - `provider`, and optionally `account`: the `id` of an account from
   *   `getConnectionStatus()` (`connections[provider][].id`)
   * @returns Success confirmation
   *
   * @example
   * ```typescript
   * // Disconnect a specific Google account
   * await client.auth.disconnect({ provider: 'google', account: 'user@gmail.com' });
   *
   * // Disconnect all Microsoft accounts
   * await client.auth.disconnect({ provider: 'microsoft' });
   * ```
   */
  async disconnect(params: DisconnectParams): Promise<DisconnectResponse> {
    const queryParams = new URLSearchParams({ provider: params.provider });
    if (params.account) {
      queryParams.set('account', params.account);
    }

    const response = await this.client.post<DisconnectResponse>(
      `/oauth/disconnect?${queryParams.toString()}`,
      {}
    );

    return response.data;
  }

  /**
   * Revoke the user's Connect access and refresh tokens
   *
   * Revokes the whole grant: every access and refresh token issued for this user. It does
   * not disconnect calendar accounts or stop webhook subscriptions. To remove a user
   * entirely, call `disconnect()` for each provider first (it needs a valid access token),
   * then `revoke()`.
   *
   * @param token - Access or refresh token to revoke. Defaults to the stored refresh token,
   *   else the stored access token; the stored credentials are then cleared.
   * @returns `{ success: true }`, also for an unknown or already revoked token
   * @throws {ValidationError} When no token is given and none is stored
   *
   * @example
   * ```typescript
   * await client.auth.disconnect({ provider: 'google' });
   * await client.auth.revoke();
   *
   * // Or revoke a token you stored yourself
   * await client.auth.revoke(storedRefreshToken);
   * ```
   */
  async revoke(token?: string): Promise<RevokeResponse> {
    const credentials = this.client.getCredentials();
    const revokeToken = token ?? credentials?.refresh_token ?? credentials?.access_token;
    if (!revokeToken) {
      throw new ValidationError('No token to revoke: pass one or set credentials first');
    }

    const response = await this.client.post<RevokeResponse>(
      '/oauth/revoke',
      { token: revokeToken },
      { skipAuthRefresh: true }
    );

    if (token === undefined) {
      this.client.clearCredentials();
    }

    return response.data;
  }
}
