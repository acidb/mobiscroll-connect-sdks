import { Calendars } from '../resources/calendars';
import { ApiClient } from '../client';
import { Calendar } from '../types';

jest.mock('../client');

describe('Calendars Resource', () => {
  let calendars: Calendars;
  let mockApiClient: jest.Mocked<ApiClient>;

  beforeEach(() => {
    mockApiClient = new ApiClient({
      apiKey: 'test-key',
    }) as jest.Mocked<ApiClient>;

    calendars = new Calendars(mockApiClient);
  });

  describe('list', () => {
    it('should call client.get with correct path', async () => {
      const mockCalendars: Calendar[] = [
        {
          id: 'work@company.com',
          title: 'My Calendar',
          provider: 'google',
          timeZone: 'America/Los_Angeles',
          color: '#9fc6e7',
          accessRole: 'owner',
        },
        {
          id: 'AAMkAGI2T...',
          title: 'Work Calendar',
          provider: 'microsoft',
          timeZone: 'UTC',
          accessRole: 'owner',
        },
        {
          id: 'E2857962-EE43-4E90-829C-A826D534C0D9',
          title: 'Personal',
          provider: 'apple',
          timeZone: 'America/New_York',
        },
      ];

      mockApiClient.get.mockResolvedValue({
        data: mockCalendars,
        status: 200,
        headers: {},
      });

      const result = await calendars.list();

      expect(mockApiClient.get).toHaveBeenCalledWith('/calendars', { headers: {} });
      expect(result).toEqual(mockCalendars);
      expect(result).toHaveLength(3);
    });

    it('should call client.get with accessToken', async () => {
      const mockCalendars: Calendar[] = [];
      mockApiClient.get.mockResolvedValue({
        data: mockCalendars,
        status: 200,
        headers: {},
      });

      await calendars.list({ accessToken: 'test-token' });

      expect(mockApiClient.get).toHaveBeenCalledWith('/calendars', {
        headers: { Authorization: 'Bearer test-token' },
      });
    });

    it('should return calendars from multiple providers', async () => {
      const mockCalendars: Calendar[] = [
        {
          id: 'google-cal-1',
          title: 'Google Calendar',
          provider: 'google',
        },
        {
          id: 'ms-cal-1',
          title: 'Microsoft Calendar',
          provider: 'microsoft',
        },
        {
          id: 'apple-cal-1',
          title: 'Apple Calendar',
          provider: 'apple',
        },
      ];

      mockApiClient.get.mockResolvedValue({
        data: mockCalendars,
        status: 200,
        headers: {},
      });

      const result = await calendars.list();

      expect(result.some((cal) => cal.provider === 'google')).toBe(true);
      expect(result.some((cal) => cal.provider === 'microsoft')).toBe(true);
      expect(result.some((cal) => cal.provider === 'apple')).toBe(true);
    });

    it('should handle empty calendar list', async () => {
      mockApiClient.get.mockResolvedValue({
        data: [],
        status: 200,
        headers: {},
      });

      const result = await calendars.list();

      expect(mockApiClient.get).toHaveBeenCalledWith('/calendars', { headers: {} });
      expect(result).toEqual([]);
    });

    it('should include calendar metadata', async () => {
      const mockCalendars: Calendar[] = [
        {
          id: 'cal-1',
          title: 'Test Calendar',
          provider: 'google',
          description: 'Test description',
          timeZone: 'America/New_York',
          color: '#ff0000',
          accessRole: 'writer',
          selected: true,
        },
      ];

      mockApiClient.get.mockResolvedValue({
        data: mockCalendars,
        status: 200,
        headers: {},
      });

      const result = await calendars.list();

      expect(result[0]).toMatchObject({
        id: 'cal-1',
        title: 'Test Calendar',
        provider: 'google',
        description: 'Test description',
        timeZone: 'America/New_York',
        color: '#ff0000',
        accessRole: 'writer',
        selected: true,
      });
    });
  });
});
