import { Auth } from '../resources/auth';
import { ApiClient } from '../client';
import { TokenResponse, ConnectionStatusResponse, DisconnectResponse } from '../types';

jest.mock('../client');

describe('Auth Resource', () => {
  let auth: Auth;
  let mockApiClient: jest.Mocked<ApiClient>;

  beforeEach(() => {
    mockApiClient = new ApiClient({
      apiKey: 'test-key',
    }) as jest.Mocked<ApiClient>;

    Object.defineProperty(mockApiClient, 'baseURL', {
      get: jest.fn(() => 'https://connect.mobiscroll.com/api'),
    });

    auth = new Auth(mockApiClient);
  });

  describe('getAuthorizationUrl', () => {
    it('should generate correct authorization URL with all parameters', () => {
      const url = auth.getAuthorizationUrl({
        clientId: 'test-client',
        userId: 'user-123',
        userName: 'John Doe',
        userEmail: 'john@example.com',
        redirectUri: 'https://app.example.com/callback',
        state: 'random-state',
      });

      expect(url).toContain('https://connect.mobiscroll.com/api/oauth/authorize');
      expect(url).toContain('response_type=code');
      expect(url).toContain('client_id=test-client');
      expect(url).toContain('user_id=user-123');
      expect(url).toContain('user_name=John+Doe');
      expect(url).toContain('user_email=john%40example.com');
      expect(url).toContain('redirect_uri=https%3A%2F%2Fapp.example.com%2Fcallback');
      expect(url).toContain('state=random-state');
    });

    it('should generate URL with only required parameters', () => {
      const url = auth.getAuthorizationUrl({
        clientId: 'test-client',
        userId: 'user-123',
      });

      expect(url).toContain('https://connect.mobiscroll.com/api/oauth/authorize');
      expect(url).toContain('response_type=code');
      expect(url).toContain('client_id=test-client');
      expect(url).toContain('user_id=user-123');
      expect(url).not.toContain('user_name');
      expect(url).not.toContain('user_email');
    });
  });

  describe('exchangeCodeForToken', () => {
    it('should exchange code for token with all parameters', async () => {
      const mockResponse: TokenResponse = {
        access_token: 'test-access-token',
        token_type: 'Bearer',
        expires_in: 3600,
      };

      mockApiClient.post.mockResolvedValue({
        data: mockResponse,
        status: 200,
        headers: {},
      });

      const result = await auth.exchangeCodeForToken(
        'auth-code-123',
        'test-client',
        'test-secret',
        'https://app.example.com/callback'
      );

      expect(mockApiClient.post).toHaveBeenCalledWith(
        '/oauth/token',
        expect.stringContaining('grant_type=authorization_code'),
        expect.objectContaining({
          headers: expect.objectContaining({
            'Content-Type': 'application/x-www-form-urlencoded',
            Authorization: expect.stringContaining('Basic '),
            CLIENT_ID: 'test-client',
          }),
        })
      );
      expect(result).toEqual(mockResponse);
      expect(result.access_token).toBe('test-access-token');
    });

    it('should exchange code without redirect_uri', async () => {
      const mockResponse: TokenResponse = {
        access_token: 'test-access-token',
        token_type: 'Bearer',
      };

      mockApiClient.post.mockResolvedValue({
        data: mockResponse,
        status: 200,
        headers: {},
      });

      await auth.exchangeCodeForToken('auth-code-123', 'test-client', 'test-secret');

      const callArgs = mockApiClient.post.mock.calls[0];
      const bodyString = callArgs[1] as string;

      expect(bodyString).toContain('grant_type=authorization_code');
      expect(bodyString).toContain('code=auth-code-123');
      expect(bodyString).not.toContain('redirect_uri');
    });
  });

  describe('getConnectionStatus', () => {
    it('should get connection status without accessToken', async () => {
      const mockResponse: ConnectionStatusResponse = {
        connections: {
          google: [{ id: 'user@gmail.com', display: 'user@gmail.com' }],
          microsoft: [{ id: 'user@outlook.com', display: 'user@outlook.com' }],
          apple: [],
        },
        limitReached: false,
        limit: 10,
      };

      mockApiClient.get.mockResolvedValue({
        data: mockResponse,
        status: 200,
        headers: {},
      });

      const result = await auth.getConnectionStatus();

      expect(mockApiClient.get).toHaveBeenCalledWith('/oauth/connection-status', { headers: {} });
      expect(result).toEqual(mockResponse);
      expect(result.connections.google).toHaveLength(1);
    });

    it('should get connection status with accessToken', async () => {
      const mockResponse: ConnectionStatusResponse = {
        connections: {
          google: [],
          microsoft: [],
          apple: [],
        },
      };

      mockApiClient.get.mockResolvedValue({
        data: mockResponse,
        status: 200,
        headers: {},
      });

      await auth.getConnectionStatus({ accessToken: 'test-token' });

      expect(mockApiClient.get).toHaveBeenCalledWith('/oauth/connection-status', {
        headers: { Authorization: 'Bearer test-token' },
      });
    });
  });

  describe('disconnect', () => {
    it('should disconnect specific account', async () => {
      const mockResponse: DisconnectResponse = { success: true };

      mockApiClient.post.mockResolvedValue({
        data: mockResponse,
        status: 200,
        headers: {},
      });

      const result = await auth.disconnect(
        { provider: 'google', account: 'user@gmail.com' },
        { accessToken: 'test-token' }
      );

      expect(mockApiClient.post).toHaveBeenCalledWith(
        '/oauth/disconnect?provider=google&account=user%40gmail.com',
        {},
        { headers: { Authorization: 'Bearer test-token' } }
      );
      expect(result.success).toBe(true);
    });

    it('should disconnect all accounts for provider', async () => {
      const mockResponse: DisconnectResponse = { success: true };

      mockApiClient.post.mockResolvedValue({
        data: mockResponse,
        status: 200,
        headers: {},
      });

      await auth.disconnect({ provider: 'microsoft' });

      expect(mockApiClient.post).toHaveBeenCalledWith(
        '/oauth/disconnect?provider=microsoft',
        {},
        { headers: {} }
      );
    });

    it('should disconnect provider with accessToken', async () => {
      const mockResponse: DisconnectResponse = { success: true };

      mockApiClient.post.mockResolvedValue({
        data: mockResponse,
        status: 200,
        headers: {},
      });

      await auth.disconnect({ provider: 'apple' }, { accessToken: 'test-token' });

      expect(mockApiClient.post).toHaveBeenCalledWith(
        '/oauth/disconnect?provider=apple',
        {},
        { headers: { Authorization: 'Bearer test-token' } }
      );
    });
  });
});
