import { ApiClient } from '../client';
import {
  AuthorizeParams,
  TokenResponse,
  ConnectionStatusResponse,
  DisconnectParams,
  DisconnectResponse,
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
   * const authUrl = client.auth.getAuthorizationUrl({
   *   clientId: 'your-client-id',
   *   userId: 'user-123',
   *   userName: 'John Doe',
   *   userEmail: 'john@example.com',
   *   redirectUri: 'https://yourapp.com/callback',
   *   state: 'optional-state-value',
   * });
   *
   * // Redirect user to authUrl
   * window.location.href = authUrl;
   * ```
   */
  getAuthorizationUrl(params: AuthorizeParams): string {
    const { clientId, userId, userName, userEmail, redirectUri, state } = params;

    const queryParams = new URLSearchParams({
      response_type: 'code',
      client_id: clientId,
      user_id: userId,
    });

    if (userName) queryParams.set('user_name', userName);
    if (userEmail) queryParams.set('user_email', userEmail);
    if (redirectUri) queryParams.set('redirect_uri', redirectUri);
    if (state) queryParams.set('state', state);

    const baseURL = this.client.baseURL;
    return `${baseURL}/oauth/authorize?${queryParams.toString()}`;
  }

  /**
   * Exchange an authorization code for an access token
   *
   * @param code - Authorization code from the OAuth callback
   * @param clientId - Client application identifier
   * @param clientSecret - Client secret for authentication
   * @param redirectUri - Must match the redirect_uri used in authorization
   * @returns Access token and related information
   *
   * @example
   * ```typescript
   * // In your callback handler:
   * const { access_token } = await client.auth.exchangeCodeForToken(
   *   authorizationCode,
   *   'your-client-id',
   *   'your-client-secret',
   *   'https://yourapp.com/callback'
   * );
   *
   * // Store the access_token securely and use it for API requests
   * ```
   */
  async exchangeCodeForToken(
    code: string,
    clientId: string,
    clientSecret: string,
    redirectUri?: string
  ): Promise<TokenResponse> {
    const body = new URLSearchParams({
      grant_type: 'authorization_code',
      code: code,
    });

    if (redirectUri) {
      body.set('redirect_uri', redirectUri);
    }

    const credentials = Buffer.from(`${clientId}:${clientSecret}`).toString('base64');

    const response = await this.client.post<TokenResponse>('/oauth/token', body.toString(), {
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${credentials}`,
        CLIENT_ID: clientId,
      },
    });

    return response.data;
  }

  /**
   * Get the connection status for all calendar providers
   *
   * Returns which provider accounts are currently connected for the authenticated user.
   *
   * @param options - Optional access token
   * @returns Connection status for all providers
   *
   * @example
   * ```typescript
   * const status = await client.auth.getConnectionStatus({ accessToken: 'your-access-token' });
   *
   * console.log(status.connections.google); // Array of connected Google accounts
   * console.log(status.connections.microsoft); // Array of connected Microsoft accounts
   * console.log(status.limitReached); // Whether account limit is reached
   * ```
   */
  async getConnectionStatus(options?: { accessToken?: string }): Promise<ConnectionStatusResponse> {
    const headers: Record<string, string> = {};
    if (options?.accessToken) {
      headers['Authorization'] = `Bearer ${options.accessToken}`;
    }

    const response = await this.client.get<ConnectionStatusResponse>('/oauth/connection-status', {
      headers,
    });
    return response.data;
  }

  /**
   * Disconnect a calendar provider account
   *
   * Removes stored tokens and revokes access for a specific provider account
   * or all accounts of a provider.
   *
   * @param params - Disconnect parameters
   * @param options - Optional access token
   * @returns Success confirmation
   *
   * @example
   * ```typescript
   * // Disconnect a specific Google account
   * await client.auth.disconnect(
   *   { provider: 'google', account: 'user@gmail.com' },
   *   { accessToken: 'your-access-token' }
   * );
   *
   * // Disconnect all Microsoft accounts
   * await client.auth.disconnect(
   *   { provider: 'microsoft' },
   *   { accessToken: 'your-access-token' }
   * );
   * ```
   */
  async disconnect(
    params: DisconnectParams,
    options?: { accessToken?: string }
  ): Promise<DisconnectResponse> {
    const queryParams = new URLSearchParams({ provider: params.provider });
    if (params.account) {
      queryParams.set('account', params.account);
    }

    const headers: Record<string, string> = {};
    if (options?.accessToken) {
      headers['Authorization'] = `Bearer ${options.accessToken}`;
    }

    const response = await this.client.post<DisconnectResponse>(
      `/oauth/disconnect?${queryParams.toString()}`,
      {},
      { headers }
    );

    return response.data;
  }
}
