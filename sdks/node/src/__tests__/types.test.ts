import {
  MobiscrollConnectError,
  AuthenticationError,
  CalendarPermissionError,
  NotFoundError,
  ValidationError,
  RateLimitError,
  ServerError,
  NetworkError,
} from '../types';

describe('Errors', () => {
  describe('MobiscrollConnectError', () => {
    it('should create error with message', () => {
      const error = new MobiscrollConnectError('Test error');
      expect(error.message).toBe('Test error');
      expect(error.name).toBe('MobiscrollConnectError');
      expect(error.code).toBeUndefined();
    });

    it('should create error with message and code', () => {
      const error = new MobiscrollConnectError('Test error', 'TEST_CODE');
      expect(error.message).toBe('Test error');
      expect(error.code).toBe('TEST_CODE');
    });
  });

  describe('AuthenticationError', () => {
    it('should create error with message', () => {
      const error = new AuthenticationError('Authentication failed');
      expect(error.message).toBe('Authentication failed');
      expect(error.name).toBe('AuthenticationError');
      expect(error.code).toBe('AUTHENTICATION_ERROR');
    });

    it('should create error with custom message', () => {
      const error = new AuthenticationError('Invalid token');
      expect(error.message).toBe('Invalid token');
    });
  });

  describe('CalendarPermissionError', () => {
    it('carries the accounts that must reconnect', () => {
      const error = new CalendarPermissionError('No connected account has calendar access.', [
        { provider: 'google', account: 'withheld@gmail.com' },
      ]);
      expect(error.name).toBe('CalendarPermissionError');
      expect(error.code).toBe('CALENDAR_PERMISSION_REQUIRED');
      expect(error.accounts).toEqual([{ provider: 'google', account: 'withheld@gmail.com' }]);
    });

    it('is an AuthenticationError, so existing handlers still catch it', () => {
      const error = new CalendarPermissionError('nope');
      expect(error).toBeInstanceOf(AuthenticationError);
      expect(error.accounts).toEqual([]);
    });
  });

  describe('NotFoundError', () => {
    it('should create error with message', () => {
      const error = new NotFoundError('Resource not found');
      expect(error.message).toBe('Resource not found');
      expect(error.name).toBe('NotFoundError');
      expect(error.code).toBe('NOT_FOUND_ERROR');
    });
  });

  describe('ValidationError', () => {
    it('should create error with details', () => {
      const details = { field: 'email', message: 'Invalid format' };
      const error = new ValidationError('Validation failed', details);
      expect(error.message).toBe('Validation failed');
      expect(error.name).toBe('ValidationError');
      expect(error.details).toEqual(details);
    });
  });

  describe('RateLimitError', () => {
    it('should create error with retry after', () => {
      const error = new RateLimitError('Rate limit exceeded', 60);
      expect(error.message).toBe('Rate limit exceeded');
      expect(error.name).toBe('RateLimitError');
      expect(error.retryAfter).toBe(60);
    });
  });

  describe('ServerError', () => {
    it('should create error with status code', () => {
      const error = new ServerError('Internal server error', 500);
      expect(error.message).toBe('Internal server error');
      expect(error.name).toBe('ServerError');
    });
  });

  describe('NetworkError', () => {
    it('should create error with message', () => {
      const error = new NetworkError('Network request failed');
      expect(error.message).toBe('Network request failed');
      expect(error.name).toBe('NetworkError');
      expect(error.code).toBe('NETWORK_ERROR');
    });
  });
});
