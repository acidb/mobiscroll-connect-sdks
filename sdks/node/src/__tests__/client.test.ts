import { MobiscrollConnectClient } from '../index';
import { MobiscrollConnectError } from '../types';

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
