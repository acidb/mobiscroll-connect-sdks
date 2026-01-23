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
    const { userId, state, scope } = params;

    const queryParams = new URLSearchParams({
      response_type: 'code',
      client_id: clientId,
      user_id: userId,
      redirect_uri: redirectUri,
    });

    if (state) queryParams.set('state', state);
    if (scope) queryParams.set('scope', scope);

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

    const response = await this.client.post<TokenResponse>('/oauth/token', body.toString(), {
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${credentials}`,
        CLIENT_ID: clientId,
      },
    });

    this.setCredentials(response.data);
    return response.data;
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
   * ```
   */
  async getConnectionStatus(): Promise<ConnectionStatusResponse> {
    const response = await this.client.get<ConnectionStatusResponse>('/oauth/connection-status');
    return response.data;
  }

  /**
   * Disconnect a calendar provider account
   *
   * Removes stored tokens and revokes access for a specific provider account
   * or all accounts of a provider.
   *
   * @param params - Disconnect parameters
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
}
