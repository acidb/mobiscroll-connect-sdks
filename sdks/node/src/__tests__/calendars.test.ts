import { Calendars } from '../resources/calendars';
import { ApiClient } from '../client';
import { Calendar, ProviderEnum } from '../types';

jest.mock('../client');

describe('Calendars Resource', () => {
  let calendars: Calendars;
  let mockApiClient: jest.Mocked<ApiClient>;

  beforeEach(() => {
    mockApiClient = new ApiClient({
      clientId: 'id',
      clientSecret: 'secret',
      redirectUri: 'uri',
    }) as jest.Mocked<ApiClient>;

    calendars = new Calendars(mockApiClient);
  });

  describe('list', () => {
    it('should call client.get with correct path', async () => {
      const mockCalendars: Calendar[] = [
        {
          id: 'work@company.com',
          title: 'My Calendar',
          provider: ProviderEnum.Google,
          timeZone: 'America/Los_Angeles',
          color: '#9fc6e7',
          description: 'Work calendar',
          original: {},
        },
      ];

      mockApiClient.get.mockResolvedValue({
        data: mockCalendars,
        status: 200,
        headers: {},
      });

      const result = await calendars.list();

      expect(mockApiClient.get).toHaveBeenCalledWith('/calendars');
      expect(result).toEqual(mockCalendars);
    });

    it('should handle empty calendar list', async () => {
      mockApiClient.get.mockResolvedValue({
        data: [],
        status: 200,
        headers: {},
      });

      const result = await calendars.list();

      expect(mockApiClient.get).toHaveBeenCalledWith('/calendars');
      expect(result).toEqual([]);
    });
  });
});
