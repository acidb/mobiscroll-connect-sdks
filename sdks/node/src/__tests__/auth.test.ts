import { Auth } from '../resources/auth';
import { ApiClient } from '../client';
import { TokenResponse, ConnectionStatusResponse, DisconnectResponse } from '../types';

jest.mock('../client');

describe('Auth Resource', () => {
  let auth: Auth;
  let mockApiClient: jest.Mocked<ApiClient>;

  const mockConfig = {
    clientId: 'test-client',
    clientSecret: 'test-secret',
    redirectUri: 'https://app.example.com/callback',
  };

  beforeEach(() => {
    mockApiClient = new ApiClient(mockConfig) as jest.Mocked<ApiClient>;

    Object.defineProperty(mockApiClient, 'baseURL', {
      get: jest.fn(() => 'https://connect.mobiscroll.com/api'),
    });
    
    mockApiClient.getConfig.mockReturnValue(mockConfig);

    auth = new Auth(mockApiClient);
  });

  describe('generateAuthUrl', () => {
    it('should generate correct authorization URL with all parameters', () => {
      const url = auth.generateAuthUrl({
        userId: 'user-123',
        state: 'random-state',
        scope: 'read-write'
      });

      expect(url).toContain('https://connect.mobiscroll.com/api/oauth/authorize');
      expect(url).toContain('response_type=code');
      expect(url).toContain('client_id=test-client');
      expect(url).toContain('user_id=user-123');
      expect(url).toContain('redirect_uri=https%3A%2F%2Fapp.example.com%2Fcallback');
      expect(url).toContain('state=random-state');
      expect(url).toContain('scope=read-write');
    });

    it('should generate URL with only required parameters', () => {
      const url = auth.generateAuthUrl({
        userId: 'user-123',
      });

      expect(url).toContain('https://connect.mobiscroll.com/api/oauth/authorize');
      expect(url).toContain('response_type=code');
      expect(url).toContain('client_id=test-client');
      expect(url).toContain('user_id=user-123');
      expect(url).not.toContain('state=');
    });
  });

  describe('getToken', () => {
    it('should exchange code for token', async () => {
      const mockResponse: TokenResponse = {
        access_token: 'test-access-token',
        token_type: 'Bearer',
        expires_in: 3600,
      };

      mockApiClient.post = jest.fn().mockResolvedValue({
        data: mockResponse,
        status: 200,
        headers: {},
      });

      const result = await auth.getToken('auth-code-123');

      expect(mockApiClient.post).toHaveBeenCalledWith(
        '/oauth/token',
        expect.any(URLSearchParams),
        expect.objectContaining({
          headers: expect.objectContaining({
            'Content-Type': 'application/x-www-form-urlencoded',
          }),
        })
      );
      
      const call = (mockApiClient.post as jest.Mock).mock.calls[0];
      const params = call[1] as URLSearchParams;
      expect(params.get('code')).toBe('auth-code-123');
      expect(params.get('client_id')).toBe('id');
      expect(params.get('client_secret')).toBe('secret');
      expect(params.get('redirect_uri')).toBe('uri');
      expect(params.get('grant_type')).toBe('authorization_code');

      expect(result).toEqual(mockResponse);
    });
  });

  describe('getConnectionStatus', () => {
    it('should get connection status', async () => {
      const mockResponse: ConnectionStatusResponse = {
        connections: {
          google: [{ id: 'user@gmail.com', display: 'user@gmail.com' }],
          microsoft: [{ id: 'user@outlook.com', display: 'user@outlook.com' }],
          apple: [],
        },
        limitReached: false,
      };

      mockApiClient.get.mockResolvedValue({
        data: mockResponse,
        status: 200,
        headers: {},
      });

      const result = await auth.getConnectionStatus();

      expect(mockApiClient.get).toHaveBeenCalledWith('/oauth/connection-status');
      expect(result).toEqual(mockResponse);
      expect(result.connections.google).toHaveLength(1);
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
        { provider: 'google', account: 'user@gmail.com' }
      );

      expect(mockApiClient.post).toHaveBeenCalledWith(
        '/oauth/disconnect?provider=google&account=user%40gmail.com',
        {}
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
        {}
      );
    });
  });
});

