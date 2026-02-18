import { Events } from '../resources/events';
import { ApiClient } from '../client';
import {
  CalendarEvent,
  ProviderEnum,
  EventListParams,
  CreateEventData,
  UpdateEventData,
  DeleteEventParams,
} from '../types';

jest.mock('../client');

describe('Events Resource', () => {
  let events: Events;
  let mockApiClient: jest.Mocked<ApiClient>;

  beforeEach(() => {
    mockApiClient = new ApiClient({
      clientId: 'id',
      clientSecret: 'secret',
      redirectUri: 'uri',
    }) as jest.Mocked<ApiClient>;

    events = new Events(mockApiClient);
  });

  describe('list', () => {
    it('should call client.get with correct query parameters', async () => {
      const mockEvents: CalendarEvent[] = [
        {
          id: '1',
          title: 'Event 1',
          start: new Date('2023-01-01T10:00:00Z'),
          end: new Date('2023-01-01T11:00:00Z'),
          allDay: false,
          provider: ProviderEnum.Google,
          calendarId: 'cal1',
          original: {},
        },
      ];

      mockApiClient.get.mockResolvedValue({
        data: { events: mockEvents },
        status: 200,
        headers: {},
      });

      const params: EventListParams = {
        start: '2023-01-01T00:00:00Z',
        end: '2023-01-31T23:59:59Z',
        calendarIds: {
          google: ['cal1', 'cal2'],
        },
        pageSize: 50,
      };

      const result = await events.list(params);

      expect(mockApiClient.get).toHaveBeenCalled();
      expect(result).toEqual({ events: mockEvents });
    });

    it('should call client.get without params if none provided', async () => {
      const mockEvents: CalendarEvent[] = [];
      mockApiClient.get.mockResolvedValue({
        data: { events: mockEvents },
        status: 200,
        headers: {},
      });

      const result = await events.list();

      expect(mockApiClient.get).toHaveBeenCalledWith('/events');
      expect(result).toEqual({ events: mockEvents });
    });
  });

  describe('create', () => {
    it('should call client.post with correct body', async () => {
      const newEvent: CreateEventData & { provider: ProviderEnum } = {
        title: 'New Event',
        start: '2023-01-01T10:00:00Z',
        end: '2023-01-01T11:00:00Z',
        calendarId: 'cal1',
        provider: ProviderEnum.Google,
      };

      const mockResponse: CalendarEvent = {
        id: 'new-id',
        title: 'New Event',
        start: new Date('2023-01-01T10:00:00Z'),
        end: new Date('2023-01-01T11:00:00Z'),
        allDay: false,
        provider: ProviderEnum.Google,
        calendarId: 'cal1',
        original: {},
      };

      mockApiClient.post.mockResolvedValue({
        data: mockResponse,
        status: 200,
        headers: {},
      });

      const result = await events.create(newEvent);

      expect(mockApiClient.post).toHaveBeenCalledWith('/event', newEvent);
      expect(result).toEqual(mockResponse);
    });

    it('should throw error if creation fails', async () => {
      const newEvent: CreateEventData & { provider: ProviderEnum } = {
        title: 'New Event',
        calendarId: 'cal1',
        start: '2023-01-01T10:00:00Z',
        end: '2023-01-01T11:00:00Z',
        provider: ProviderEnum.Google,
      };
      const error = new Error('Creation failed');

      mockApiClient.post.mockRejectedValue(error);

      await expect(events.create(newEvent)).rejects.toThrow('Creation failed');
    });
  });

  describe('update', () => {
    it('should call client.put with correct body', async () => {
      const updateData: UpdateEventData & { provider: ProviderEnum } = {
        eventId: 'event-id',
        title: 'Updated Event',
        calendarId: 'cal1',
        provider: ProviderEnum.Google,
      };

      const mockResponse: CalendarEvent = {
        id: 'event-id',
        title: 'Updated Event',
        start: new Date('2023-01-01T10:00:00Z'),
        end: new Date('2023-01-01T11:00:00Z'),
        allDay: false,
        provider: ProviderEnum.Google,
        calendarId: 'cal1',
        original: {},
      };

      mockApiClient.put.mockResolvedValue({
        data: mockResponse,
        status: 200,
        headers: {},
      });

      const result = await events.update(updateData);

      expect(mockApiClient.put).toHaveBeenCalledWith('/event', updateData);
      expect(result).toEqual(mockResponse);
    });
  });

  describe('delete', () => {
    it('should call client.delete with correct id', async () => {
      mockApiClient.delete.mockResolvedValue({
        data: {},
        status: 204,
        headers: {},
      });

      const deleteParams: DeleteEventParams = {
        eventId: 'event-id',
        calendarId: 'calendar-id',
        provider: ProviderEnum.Google,
      };

      await events.delete(deleteParams);

      expect(mockApiClient.delete).toHaveBeenCalled();
    });
  });
});
