import { MobiscrollConnectClient } from '../index';
import { MobiscrollConnectError } from '../types';

describe('MobiscrollConnectClient', () => {
  describe('constructor', () => {
    it('should throw error if API key is not provided', () => {
      expect(() => {
        new MobiscrollConnectClient({ apiKey: '' });
      }).toThrow(MobiscrollConnectError);
    });

    it('should create client with valid config', () => {
      const client = new MobiscrollConnectClient({
        apiKey: 'test-key',
      });

      expect(client).toBeInstanceOf(MobiscrollConnectClient);
      expect(client.calendars).toBeDefined();
      expect(client.events).toBeDefined();
    });

    it('should use default baseURL if not provided', () => {
      const client = new MobiscrollConnectClient({
        apiKey: 'test-key',
      });

      const config = client.getConfig();
      expect(config.baseURL).toBe('https://connect.mobiscroll.com/api');
    });

    it('should use custom baseURL if provided', () => {
      const client = new MobiscrollConnectClient({
        apiKey: 'test-key',
        baseURL: 'https://custom.api.com',
      });

      const config = client.getConfig();
      expect(config.baseURL).toBe('https://custom.api.com');
    });

    it('should use default timeout if not provided', () => {
      const client = new MobiscrollConnectClient({
        apiKey: 'test-key',
      });

      const config = client.getConfig();
      expect(config.timeout).toBe(30000);
    });
  });

  describe('setApiKey', () => {
    it('should update the API key', () => {
      const client = new MobiscrollConnectClient({
        apiKey: 'old-key',
      });

      client.setApiKey('new-key');

      const config = client.getConfig();
      expect(config.apiKey).toBe('new-key');
    });
  });

  describe('getConfig', () => {
    it('should return a copy of the config', () => {
      const client = new MobiscrollConnectClient({
        apiKey: 'test-key',
      });

      const config1 = client.getConfig();
      const config2 = client.getConfig();

      expect(config1).toEqual(config2);
      expect(config1).not.toBe(config2);
    });
  });

  describe('resources', () => {
    it('should have calendars resource', () => {
      const client = new MobiscrollConnectClient({
        apiKey: 'test-key',
      });

      expect(client.calendars).toBeDefined();
      expect(typeof client.calendars.list).toBe('function');
    });

    it('should have events resource', () => {
      const client = new MobiscrollConnectClient({
        apiKey: 'test-key',
      });

      expect(client.events).toBeDefined();
      expect(typeof client.events.list).toBe('function');
    });
  });
});
