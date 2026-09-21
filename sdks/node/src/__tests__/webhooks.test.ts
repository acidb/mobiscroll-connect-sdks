import { Webhooks } from '../resources/webhooks';
import { ApiClient } from '../client';
import {
  ProviderEnum,
  SubscribeWebhookParams,
  SubscribeWebhookResponse,
  UnsubscribeWebhookParams,
  UnsubscribeWebhookResponse,
  ValidationError,
  AuthenticationError,
  ServerError,
} from '../types';

jest.mock('../client');

describe('Webhooks Resource', () => {
  let webhooks: Webhooks;
  let mockApiClient: jest.Mocked<ApiClient>;

  beforeEach(() => {
    mockApiClient = new ApiClient({
      clientId: 'id',
      clientSecret: 'secret',
      redirectUri: 'uri',
    }) as jest.Mocked<ApiClient>;

    webhooks = new Webhooks(mockApiClient);
  });

  describe('subscribeWebhook', () => {
    it('should call client.post with correct path and body', async () => {
      const params: SubscribeWebhookParams = {
        provider: ProviderEnum.Google,
        calendarId: 'primary',
      };

      const mockResponse: SubscribeWebhookResponse = {
        success: true,
        provider: 'google',
        subscription: {
          channelId: 'channel-123',
          resourceId: 'resource-456',
          expiration: '2026-10-01T00:00:00.000Z',
        },
        serverWebhookUrl: 'https://connect.mobiscroll.com/api/webhook-callback',
        channelId: 'channel-123',
      };

      mockApiClient.post.mockResolvedValue({
        data: mockResponse,
        status: 200,
        headers: {},
      });

      const result = await webhooks.subscribeWebhook(params);

      expect(mockApiClient.post).toHaveBeenCalledWith('/subscribe-webhook', params);
      expect(result).toEqual(mockResponse);
    });

    it('should pass optional channelId and expiration through', async () => {
      const params: SubscribeWebhookParams = {
        provider: ProviderEnum.Microsoft,
        calendarId: 'AAMk==',
        channelId: 'custom-channel',
        expiration: 1893456000000,
      };

      const mockResponse: SubscribeWebhookResponse = {
        success: true,
        provider: 'microsoft',
        subscription: { channelId: 'custom-channel' },
        serverWebhookUrl: 'https://connect.mobiscroll.com/api/webhook-callback',
        channelId: 'custom-channel',
      };

      mockApiClient.post.mockResolvedValue({
        data: mockResponse,
        status: 200,
        headers: {},
      });

      const result = await webhooks.subscribeWebhook(params);

      expect(mockApiClient.post).toHaveBeenCalledWith('/subscribe-webhook', params);
      expect(result).toEqual(mockResponse);
    });

    it('should propagate a ValidationError for a missing provider/calendarId (400)', async () => {
      const params = { calendarId: 'primary' } as unknown as SubscribeWebhookParams;
      mockApiClient.post.mockRejectedValue(new ValidationError('provider is required'));

      await expect(webhooks.subscribeWebhook(params)).rejects.toThrow(ValidationError);
    });

    it('should propagate an AuthenticationError for a bad/missing bearer token (401)', async () => {
      const params: SubscribeWebhookParams = {
        provider: ProviderEnum.Google,
        calendarId: 'primary',
      };
      mockApiClient.post.mockRejectedValue(new AuthenticationError('Invalid token'));

      await expect(webhooks.subscribeWebhook(params)).rejects.toThrow(AuthenticationError);
    });

    it('should propagate a ServerError (500)', async () => {
      const params: SubscribeWebhookParams = {
        provider: ProviderEnum.Google,
        calendarId: 'primary',
      };
      mockApiClient.post.mockRejectedValue(new ServerError('Internal server error', 500));

      await expect(webhooks.subscribeWebhook(params)).rejects.toThrow(ServerError);
    });
  });

  describe('unsubscribeWebhook', () => {
    it('should call client.post with correct path and body', async () => {
      const params: UnsubscribeWebhookParams = {
        provider: ProviderEnum.Google,
        channelId: 'channel-123',
        resourceId: 'resource-456',
      };

      const mockResponse: UnsubscribeWebhookResponse = { success: true };

      mockApiClient.post.mockResolvedValue({
        data: mockResponse,
        status: 200,
        headers: {},
      });

      const result = await webhooks.unsubscribeWebhook(params);

      expect(mockApiClient.post).toHaveBeenCalledWith('/unsubscribe-webhook', params);
      expect(result).toEqual(mockResponse);
    });

    it('should treat success: true with an explanatory message as final', async () => {
      const params: UnsubscribeWebhookParams = {
        provider: ProviderEnum.Google,
        channelId: 'channel-123',
      };

      const mockResponse: UnsubscribeWebhookResponse = {
        success: true,
        message: 'Subscription had already expired upstream; local mapping removed.',
      };

      mockApiClient.post.mockResolvedValue({
        data: mockResponse,
        status: 200,
        headers: {},
      });

      const result = await webhooks.unsubscribeWebhook(params);

      expect(result.success).toBe(true);
      expect(result.message).toBeDefined();
    });

    it('should propagate a ValidationError for a missing provider/channelId (400)', async () => {
      const params = { provider: ProviderEnum.Google } as unknown as UnsubscribeWebhookParams;
      mockApiClient.post.mockRejectedValue(new ValidationError('channelId is required'));

      await expect(webhooks.unsubscribeWebhook(params)).rejects.toThrow(ValidationError);
    });

    it('should propagate an AuthenticationError for a bad/missing bearer token (401)', async () => {
      const params: UnsubscribeWebhookParams = {
        provider: ProviderEnum.Google,
        channelId: 'channel-123',
      };
      mockApiClient.post.mockRejectedValue(new AuthenticationError('Invalid token'));

      await expect(webhooks.unsubscribeWebhook(params)).rejects.toThrow(AuthenticationError);
    });

    it('should propagate a ServerError (500)', async () => {
      const params: UnsubscribeWebhookParams = {
        provider: ProviderEnum.Google,
        channelId: 'channel-123',
      };
      mockApiClient.post.mockRejectedValue(new ServerError('Internal server error', 500));

      await expect(webhooks.unsubscribeWebhook(params)).rejects.toThrow(ServerError);
    });
  });
});
