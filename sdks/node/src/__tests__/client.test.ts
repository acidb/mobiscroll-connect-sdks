import { MobiscrollConnectClient, ApiClient } from '../index';
import { Auth } from '../resources/auth';
import axios, { AxiosError } from 'axios';
import { AuthenticationError, MobiscrollConnectError } from '../types';

describe('MobiscrollConnectClient', () => {
  const validConfig = {
    clientId: 'test-client-id',
    clientSecret: 'test-client-secret',
    redirectUri: 'http://localhost/callback',
  };

  describe('constructor', () => {
    it('should throw error if config is missing required fields', () => {
      expect(() => {
        new MobiscrollConnectClient({ clientId: '', clientSecret: '', redirectUri: '' });
      }).toThrow(MobiscrollConnectError);
    });

    it('should create client with valid config', () => {
      const client = new MobiscrollConnectClient(validConfig);

      expect(client).toBeInstanceOf(MobiscrollConnectClient);
      expect(client.calendars).toBeDefined();
      expect(client.events).toBeDefined();
    });

    it('should use default baseURL', () => {
      const client = new MobiscrollConnectClient(validConfig);
      const config = client.getConfig();
      expect(config.clientId).toBe(validConfig.clientId);
    });
  });

  describe('setCredentials', () => {
    it('should update the credentials', () => {
      const client = new MobiscrollConnectClient(validConfig);

      const tokens = {
        access_token: 'new-token',
        token_type: 'Bearer',
      };

      client.setCredentials(tokens);
    });
  });

  describe('auth.revoke', () => {
    const tokens = {
      access_token: 'stored-access',
      refresh_token: 'stored-refresh',
      token_type: 'Bearer',
    };

    it('should clear stored credentials after revoking them', async () => {
      const apiClient = new ApiClient(validConfig);
      apiClient.setCredentials(tokens);
      const requests: { url?: string; data?: unknown }[] = [];
      apiClient.axiosInstance.defaults.adapter = async (config) => {
        requests.push({ url: config.url, data: config.data });
        return { data: { success: true }, status: 200, statusText: 'OK', headers: {}, config };
      };

      await expect(new Auth(apiClient).revoke()).resolves.toEqual({ success: true });

      expect(requests).toEqual([{ url: '/oauth/revoke', data: '{"token":"stored-refresh"}' }]);
      expect(apiClient.getCredentials()).toBeUndefined();
    });

    it('should not refresh tokens when the revoke request returns 401', async () => {
      const apiClient = new ApiClient(validConfig);
      apiClient.setCredentials(tokens);
      const urls: (string | undefined)[] = [];
      apiClient.axiosInstance.defaults.adapter = async (config) => {
        urls.push(config.url);
        const response = { data: {}, status: 401, statusText: 'Unauthorized', headers: {}, config };
        throw new AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, null, response);
      };

      const refresh = jest.spyOn(axios, 'post');

      await expect(new Auth(apiClient).revoke()).rejects.toBeInstanceOf(AuthenticationError);

      expect(refresh).not.toHaveBeenCalled();
      expect(urls).toEqual(['/oauth/revoke']);
      refresh.mockRestore();
      expect(apiClient.getCredentials()).toEqual(tokens);
    });
  });

  describe('getConfig', () => {
    it('should return the config', () => {
      const client = new MobiscrollConnectClient(validConfig);

      const config = client.getConfig();
      expect(config.clientId).toBe(validConfig.clientId);
    });
  });

  describe('resources', () => {
    it('should have calendars resource', () => {
      const client = new MobiscrollConnectClient(validConfig);

      expect(client.calendars).toBeDefined();
      expect(typeof client.calendars.list).toBe('function');
    });

    it('should have events resource', () => {
      const client = new MobiscrollConnectClient(validConfig);

      expect(client.events).toBeDefined();
      expect(typeof client.events.list).toBe('function');
    });
  });
});
