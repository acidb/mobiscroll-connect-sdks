import { Events } from '../resources/events';
import { ApiClient } from '../client';
import { Event, EventsListResponse, EventResponse, DeleteEventResponse } from '../types';

jest.mock('../client');

describe('Events Resource', () => {
  let events: Events;
  let mockApiClient: jest.Mocked<ApiClient>;

  beforeEach(() => {
    mockApiClient = new ApiClient({
      apiKey: 'test-key',
    }) as jest.Mocked<ApiClient>;

    events = new Events(mockApiClient);
  });

  describe('list', () => {
    it('should call client.get with correct path for basic request', async () => {
      const mockResponse: EventsListResponse = {
        events: [],
        pageSize: 250,
      };
      mockApiClient.get.mockResolvedValue({
        data: mockResponse,
        status: 200,
        headers: {},
      });

      await events.list();

      expect(mockApiClient.get).toHaveBeenCalledWith('/events', { headers: {} });
    });

    it('should call client.get with pageSize', async () => {
      const mockResponse: EventsListResponse = {
        events: [],
        pageSize: 50,
      };
      mockApiClient.get.mockResolvedValue({
        data: mockResponse,
        status: 200,
        headers: {},
      });

      await events.list({ pageSize: 50 });

      expect(mockApiClient.get).toHaveBeenCalledWith('/events?pageSize=50', { headers: {} });
    });

    it('should limit pageSize to 1000', async () => {
      const mockResponse: EventsListResponse = {
        events: [],
        pageSize: 1000,
      };
      mockApiClient.get.mockResolvedValue({
        data: mockResponse,
        status: 200,
        headers: {},
      });

      await events.list({ pageSize: 5000 });

      expect(mockApiClient.get).toHaveBeenCalledWith('/events?pageSize=1000', { headers: {} });
    });

    it('should send filters as separate query params', async () => {
      const mockResponse: EventsListResponse = {
        events: [],
        pageSize: 250,
      };
      mockApiClient.get.mockResolvedValue({
        data: mockResponse,
        status: 200,
        headers: {},
      });

      await events.list({
        filters: {
          start: '2025-10-01T00:00:00Z',
          end: '2025-10-31T23:59:59Z',
          calendarIds: {
            google: ['cal1'],
            microsoft: [],
            apple: [],
          },
        },
      });

      const expectedCalendarIds = JSON.stringify({
        google: ['cal1'],
        microsoft: [],
        apple: [],
      });
      expect(mockApiClient.get).toHaveBeenCalledWith(
        `/events?start=2025-10-01T00%3A00%3A00Z&end=2025-10-31T23%3A59%3A59Z&calendarIds=${encodeURIComponent(
          expectedCalendarIds
        )}`,
        { headers: {} }
      );
    });

    it('should handle paging parameter', async () => {
      const mockResponse: EventsListResponse = {
        events: [],
        pageSize: 50,
        paging: 'base64encodedpagingstate',
      };
      mockApiClient.get.mockResolvedValue({
        data: mockResponse,
        status: 200,
        headers: {},
      });

      await events.list({
        pageSize: 50,
        paging: 'base64encodedpagingstate',
      });

      expect(mockApiClient.get).toHaveBeenCalledWith(
        '/events?pageSize=50&paging=base64encodedpagingstate',
        { headers: {} }
      );
    });

    it('should handle singleEvents parameter', async () => {
      const mockResponse: EventsListResponse = {
        events: [],
        pageSize: 250,
      };
      mockApiClient.get.mockResolvedValue({
        data: mockResponse,
        status: 200,
        headers: {},
      });

      await events.list({ singleEvents: false });

      expect(mockApiClient.get).toHaveBeenCalledWith('/events?singleEvents=false', { headers: {} });
    });

    it('should handle accessToken', async () => {
      const mockResponse: EventsListResponse = {
        events: [],
        pageSize: 250,
      };
      mockApiClient.get.mockResolvedValue({
        data: mockResponse,
        status: 200,
        headers: {},
      });

      await events.list({ accessToken: 'test-token' });

      expect(mockApiClient.get).toHaveBeenCalledWith('/events', {
        headers: { Authorization: 'Bearer test-token' },
      });
    });

    it('should return events with pagination info', async () => {
      const mockEvents: Event[] = [
        {
          id: '123',
          calendarId: 'cal123',
          title: 'Test Event',
          start: new Date('2025-01-01T10:00:00Z'),
          end: new Date('2025-01-01T11:00:00Z'),
          provider: 'google',
        },
      ];
      const mockResponse: EventsListResponse = {
        events: mockEvents,
        pageSize: 250,
        paging: 'nextPageToken',
      };
      mockApiClient.get.mockResolvedValue({
        data: mockResponse,
        status: 200,
        headers: {},
      });

      const result = await events.list();

      expect(result).toEqual(mockResponse);
      expect(result.events).toHaveLength(1);
      expect(result.paging).toBe('nextPageToken');
    });
  });

  describe('create', () => {
    it('should call client.post with correct path and data for Google', async () => {
      const mockResponse: EventResponse = {
        success: true,
        provider: 'google',
        id: '123',
        title: 'Team Meeting',
        start: new Date('2025-11-10T10:00:00Z'),
        end: new Date('2025-11-10T11:00:00Z'),
        allDay: false,
      };
      mockApiClient.post.mockResolvedValue({
        data: mockResponse,
        status: 201,
        headers: {},
      });

      const createData = {
        calendarId: 'primary',
        title: 'Team Meeting',
        start: '2025-11-10T10:00:00Z',
        end: '2025-11-10T11:00:00Z',
      };

      const result = await events.create('google', createData);

      expect(mockApiClient.post).toHaveBeenCalledWith(
        '/event',
        { ...createData, provider: 'google' },
        { headers: {} }
      );
      expect(result).toEqual(mockResponse);
    });

    it('should create event with accessToken', async () => {
      const mockResponse: EventResponse = {
        success: true,
        provider: 'google',
        id: '123',
        title: 'Team Meeting',
        start: new Date('2025-11-10T10:00:00Z'),
        end: new Date('2025-11-10T11:00:00Z'),
        allDay: false,
      };
      mockApiClient.post.mockResolvedValue({
        data: mockResponse,
        status: 201,
        headers: {},
      });

      const createData = {
        calendarId: 'primary',
        title: 'Team Meeting',
        start: '2025-11-10T10:00:00Z',
        end: '2025-11-10T11:00:00Z',
      };

      const result = await events.create('google', createData, { accessToken: 'test-token' });

      expect(mockApiClient.post).toHaveBeenCalledWith(
        '/event',
        { ...createData, provider: 'google' },
        {
          headers: { Authorization: 'Bearer test-token' },
        }
      );
      expect(result).toEqual(mockResponse);
    });

    it('should create event with recurrence', async () => {
      const mockResponse: EventResponse = {
        success: true,
        provider: 'google',
        id: '456',
        title: 'Daily Standup',
        start: new Date('2025-11-10T09:00:00Z'),
        end: new Date('2025-11-10T09:15:00Z'),
        allDay: false,
      };
      mockApiClient.post.mockResolvedValue({
        data: mockResponse,
        status: 201,
        headers: {},
      });

      const createData = {
        calendarId: 'primary',
        title: 'Daily Standup',
        start: '2025-11-10T09:00:00Z',
        end: '2025-11-10T09:15:00Z',
        recurrence: {
          frequency: 'DAILY' as const,
          count: 5,
        },
      };

      const result = await events.create('google', createData);

      expect(mockApiClient.post).toHaveBeenCalledWith(
        '/event',
        { ...createData, provider: 'google' },
        { headers: {} }
      );
      expect(result).toHaveProperty('success');
      expect(result).toHaveProperty('id');
    });

    it('should create all-day event', async () => {
      const mockResponse: EventResponse = {
        success: true,
        provider: 'microsoft',
        id: '789',
        title: 'Conference',
        start: new Date('2025-11-15T00:00:00Z'),
        end: new Date('2025-11-16T00:00:00Z'),
        allDay: true,
      };
      mockApiClient.post.mockResolvedValue({
        data: mockResponse,
        status: 201,
        headers: {},
      });

      const createData = {
        calendarId: 'cal-id',
        title: 'Conference',
        start: '2025-11-15T00:00:00Z',
        end: '2025-11-16T00:00:00Z',
        allDay: true,
      };

      const result = await events.create('microsoft', createData);

      expect(mockApiClient.post).toHaveBeenCalledWith(
        '/event',
        { ...createData, provider: 'microsoft' },
        {
          headers: {},
        }
      );
      expect(result).toHaveProperty('allDay', true);
    });
  });

  describe('update', () => {
    it('should call client.put with correct path and data', async () => {
      const mockResponse: EventResponse = {
        success: true,
        provider: 'google',
        id: '123',
        title: 'Updated Meeting',
        start: new Date('2025-11-10T14:00:00Z'),
        end: new Date('2025-11-10T15:00:00Z'),
        allDay: false,
      };
      mockApiClient.put.mockResolvedValue({
        data: mockResponse,
        status: 200,
        headers: {},
      });

      const updateData = {
        calendarId: 'primary',
        eventId: '123',
        title: 'Updated Meeting',
        start: '2025-11-10T14:00:00Z',
        end: '2025-11-10T15:00:00Z',
      };

      const result = await events.update('google', updateData);

      expect(mockApiClient.put).toHaveBeenCalledWith(
        '/event',
        { ...updateData, provider: 'google' },
        { headers: {} }
      );
      expect(result).toEqual(mockResponse);
    });

    it('should update event with accessToken', async () => {
      const mockResponse: EventResponse = {
        success: true,
        provider: 'google',
        id: '123',
        title: 'Updated Meeting',
        start: new Date('2025-11-10T14:00:00Z'),
        end: new Date('2025-11-10T15:00:00Z'),
        allDay: false,
      };
      mockApiClient.put.mockResolvedValue({
        data: mockResponse,
        status: 200,
        headers: {},
      });

      const updateData = {
        calendarId: 'primary',
        eventId: '123',
        title: 'Updated Meeting',
        start: '2025-11-10T14:00:00Z',
        end: '2025-11-10T15:00:00Z',
      };

      const result = await events.update('google', updateData, { accessToken: 'test-token' });

      expect(mockApiClient.put).toHaveBeenCalledWith(
        '/event',
        { ...updateData, provider: 'google' },
        {
          headers: { Authorization: 'Bearer test-token' },
        }
      );
      expect(result).toEqual(mockResponse);
    });

    it('should update single instance of recurring event', async () => {
      const mockResponse: EventResponse = {
        success: true,
        provider: 'microsoft',
        id: '123',
        title: 'Rescheduled Meeting',
        start: new Date('2025-11-10T14:00:00Z'),
        end: new Date('2025-11-10T15:00:00Z'),
        allDay: false,
      };
      mockApiClient.put.mockResolvedValue({
        data: mockResponse,
        status: 200,
        headers: {},
      });

      const updateData = {
        calendarId: 'primary',
        eventId: '123',
        recurringEventId: 'recurring-456',
        updateMode: 'this' as const,
        title: 'Rescheduled Meeting',
      };

      const result = await events.update('microsoft', updateData);

      expect(mockApiClient.put).toHaveBeenCalledWith(
        '/event',
        { ...updateData, provider: 'microsoft' },
        { headers: {} }
      );
      expect(result).toHaveProperty('success');
      expect(result).toHaveProperty('id');
    });

    it('should update all instances in recurring series', async () => {
      const mockResponse: EventResponse = {
        success: true,
        provider: 'apple',
        id: '123',
        title: 'Weekly Meeting',
        start: new Date('2025-11-10T10:00:00Z'),
        end: new Date('2025-11-10T11:00:00Z'),
        allDay: false,
      };
      mockApiClient.put.mockResolvedValue({
        data: mockResponse,
        status: 200,
        headers: {},
      });

      const updateData = {
        calendarId: 'cal-id',
        eventId: '123',
        updateMode: 'all' as const,
        start: '2025-11-10T10:00:00Z',
        end: '2025-11-10T11:00:00Z',
      };

      const result = await events.update('apple', updateData);

      expect(mockApiClient.put).toHaveBeenCalledWith(
        '/event',
        { ...updateData, provider: 'apple' },
        { headers: {} }
      );
      expect(result).toHaveProperty('success');
      expect(result).toHaveProperty('id');
    });
  });

  describe('delete', () => {
    it('should call client.delete with correct path and data', async () => {
      const mockResponse: DeleteEventResponse = { success: true };
      mockApiClient.delete.mockResolvedValue({
        data: mockResponse,
        status: 200,
        headers: {},
      });

      const deleteParams = {
        provider: 'google' as const,
        calendarId: 'primary',
        eventId: '123',
      };

      const result = await events.delete(deleteParams);

      expect(mockApiClient.delete).toHaveBeenCalledWith('/event', {
        data: deleteParams,
        headers: {},
      });
      expect(result).toEqual(mockResponse);
    });

    it('should delete event with accessToken', async () => {
      const mockResponse: DeleteEventResponse = { success: true };
      mockApiClient.delete.mockResolvedValue({
        data: mockResponse,
        status: 200,
        headers: {},
      });

      const deleteParams = {
        provider: 'google' as const,
        calendarId: 'primary',
        eventId: '123',
      };

      const result = await events.delete(deleteParams, { accessToken: 'test-token' });

      expect(mockApiClient.delete).toHaveBeenCalledWith('/event', {
        data: deleteParams,
        headers: { Authorization: 'Bearer test-token' },
      });
      expect(result).toEqual(mockResponse);
    });

    it('should delete single instance of recurring event', async () => {
      const mockResponse: DeleteEventResponse = { success: true };
      mockApiClient.delete.mockResolvedValue({
        data: mockResponse,
        status: 200,
        headers: {},
      });

      const deleteParams = {
        provider: 'microsoft' as const,
        calendarId: 'cal-id',
        eventId: '123',
        recurringEventId: 'recurring-456',
        deleteMode: 'this' as const,
      };

      const result = await events.delete(deleteParams);

      expect(mockApiClient.delete).toHaveBeenCalledWith('/event', {
        data: deleteParams,
        headers: {},
      });
      expect(result.success).toBe(true);
    });

    it('should delete all instances in recurring series', async () => {
      const mockResponse: DeleteEventResponse = {
        success: true,
        message: 'All instances deleted',
      };
      mockApiClient.delete.mockResolvedValue({
        data: mockResponse,
        status: 200,
        headers: {},
      });

      const deleteParams = {
        provider: 'apple' as const,
        calendarId: 'cal-id',
        eventId: '123',
        deleteMode: 'all' as const,
      };

      const result = await events.delete(deleteParams);

      expect(mockApiClient.delete).toHaveBeenCalledWith('/event', {
        data: deleteParams,
        headers: {},
      });
      expect(result.message).toBe('All instances deleted');
    });
  });
});
