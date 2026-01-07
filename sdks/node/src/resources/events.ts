import { ApiClient } from '../client';
import {
  EventListParams,
  EventsListResponse,
  CreateEventData,
  UpdateEventData,
  DeleteEventParams,
  EventResponse,
  DeleteEventResponse,
} from '../types';

/**
 * Events resource for managing calendar events across multiple providers
 */
export class Events {
  constructor(private readonly client: ApiClient) {}

  /**
   * List calendar events from all connected providers (Google, Microsoft, Apple)
   *
   * Fetches events from all configured calendar providers for the authenticated user.
   * Supports pagination, filtering by date range and calendar IDs, and load-more functionality.
   * Events are automatically sorted chronologically by start time across all providers.
   *
   * @param params - Query parameters for filtering and pagination
   * @param params.pageSize - Number of events to fetch per request (default: 250, max: 1000)
   * @param params.filters - Filters for querying events:
   *   - start: ISO date string - Filter events starting from this date
   *   - end: ISO date string - Filter events ending before this date
   *   - calendarIds: Object with provider keys and array of calendar IDs
   * @param params.paging - Base64 encoded pagination state from previous response
   * @param params.singleEvents - Controls recurring events handling:
   *   - true (default): Expands recurring events into individual instances
   *   - false: Returns only the master recurring event
   * @param params.accessToken - Access token for authentication (overrides API key)
   *
   * @returns Promise resolving to events list with pagination state
   *
   * @example
   * // First request - fetch initial events
   * const result = await client.events.list({
   *   pageSize: 50,
   *   filters: {
   *     start: '2025-10-01T00:00:00Z',
   *     end: '2025-10-31T23:59:59Z'
   *   }
   * });
   *
   * @example
   * // Load more events using paging token
   * const moreEvents = await client.events.list({
   *   pageSize: 50,
   *   paging: result.paging // from previous response
   * });
   *
   * @example
   * // Filter by specific calendars
   * const filtered = await client.events.list({
   *   pageSize: 25,
   *   filters: {
   *     calendarIds: {
   *       google: ['personal@gmail.com'],
   *       microsoft: [],
   *       apple: []
   *     }
   *   }
   * });
   *
   * @example
   * // Get recurring event series masters only
   * const recurring = await client.events.list({
   *   pageSize: 50,
   *   singleEvents: false
   * });
   */
  async list(params?: EventListParams): Promise<EventsListResponse> {
    const queryParams = new URLSearchParams();

    if (params?.pageSize) {
      const pageSize = Math.min(params.pageSize, 1000);
      queryParams.append('pageSize', pageSize.toString());
    }

    if (params?.filters) {
      if (params.filters.start !== undefined) {
        queryParams.append('start', String(params.filters.start));
      }
      if (params.filters.end !== undefined) {
        queryParams.append('end', String(params.filters.end));
      }
      if (params.filters.calendarIds) {
        queryParams.append('calendarIds', JSON.stringify(params.filters.calendarIds));
      }
    }

    if (params?.paging) {
      queryParams.append('paging', params.paging);
    }

    if (params?.singleEvents !== undefined) {
      queryParams.append('singleEvents', params.singleEvents.toString());
    }

    const query = queryParams.toString();
    const path = query ? `/events?${query}` : '/events';

    const headers: Record<string, string> = {};
    if (params?.accessToken) {
      headers['Authorization'] = `Bearer ${params.accessToken}`;
    }

    const response = await this.client.get<EventsListResponse>(path, { headers });
    return response.data;
  }

  /**
   * Create a new calendar event
   *
   * Creates a new event in the specified calendar for the given provider.
   * Supports one-time events and recurring events with flexible recurrence rules.
   *
   * @param provider - Calendar provider (google, microsoft, apple)
   * @param data - Event data to create
   * @param data.calendarId - Calendar ID where the event will be created
   * @param data.title - Event title
   * @param data.description - Event description (optional)
   * @param data.start - Start date/time in ISO format
   * @param data.end - End date/time in ISO format
   * @param data.allDay - Whether this is an all-day event (default: false)
   * @param data.location - Event location (optional)
   * @param data.recurrence - Recurrence rule for recurring events (optional)
   * @param data.attendees - List of attendee email addresses (optional)
   * @param data.custom - Custom provider-specific properties (optional)
   * @param data.availability - Event availability: 'busy' or 'free' (optional)
   * @param data.privacy - Event privacy: 'public', 'private', or 'confidential' (optional)
   * @param data.status - Event status: 'confirmed', 'tentative', or 'cancelled' (optional)
   * @param options - Optional parameters
   * @param options.accessToken - Access token for authentication (overrides API key)
   *
   * @returns Promise resolving to response with success status and normalized event properties
   *   On success, returns { success: true, provider, id, title, start, end, ...eventProperties, original }
   *   On failure, returns { success: false, message: string }
   *
   * @example
   * // Create a simple one-time event
   * const result = await client.events.create('google', {
   *   calendarId: 'primary',
   *   title: 'Team Meeting',
   *   description: 'Weekly sync',
   *   start: '2025-11-10T10:00:00Z',
   *   end: '2025-11-10T11:00:00Z'
   * });
   * // result: {
   * //   success: true,
   * //   provider: 'google',
   * //   id: 'event-id',
   * //   title: 'Team Meeting',
   * //   start: Date,
   * //   end: Date,
   * //   allDay: false,
   * //   location: undefined,
   * //   attendees: [],
   * //   original: <raw Google event object>
   * // }
   *
   * @example
   * // Create an event with attendees
   * const withAttendees = await client.events.create('microsoft', {
   *   calendarId: 'calendar-id',
   *   title: 'Project Review',
   *   start: '2025-11-12T14:00:00Z',
   *   end: '2025-11-12T15:00:00Z',
   *   attendees: ['alice@company.com', 'bob@company.com']
   * });
   *
   * @example
   * // Create an all-day event
   * const allDayEvent = await client.events.create('apple', {
   *   calendarId: 'calendar-id',
   *   title: 'Conference',
   *   start: '2025-11-15T00:00:00Z',
   *   end: '2025-11-16T00:00:00Z',
   *   allDay: true
   * });
   *
   * @example
   * // Create a recurring event (daily for 5 days)
   * const recurring = await client.events.create('google', {
   *   calendarId: 'primary',
   *   title: 'Daily Standup',
   *   start: '2025-11-10T09:00:00Z',
   *   end: '2025-11-10T09:15:00Z',
   *   recurrence: {
   *     frequency: 'DAILY',
   *     count: 5
   *   }
   * });
   *
   * @example
   * // Create a weekly recurring event on Mondays and Wednesdays
   * const weeklyRecurring = await client.events.create('google', {
   *   calendarId: 'primary',
   *   title: 'Gym Session',
   *   start: '2025-11-10T18:00:00Z',
   *   end: '2025-11-10T19:00:00Z',
   *   recurrence: {
   *     frequency: 'WEEKLY',
   *     byDay: ['MO', 'WE'],
   *     until: '20251231T235959Z'
   *   }
   * });
   */
  async create(
    provider: 'google' | 'microsoft' | 'apple',
    data: CreateEventData,
    options?: { accessToken?: string }
  ): Promise<EventResponse> {
    const headers: Record<string, string> = {};
    if (options?.accessToken) {
      headers['Authorization'] = `Bearer ${options.accessToken}`;
    }
    const bodyWithProvider = { ...data, provider };
    const response = await this.client.post<EventResponse>('/event', bodyWithProvider, {
      headers,
    });
    return response.data;
  }

  /**
   * Update an existing calendar event
   *
   * Updates an existing event in the specified calendar for the given provider.
   * Supports updating single events and recurring event instances with different update modes.
   *
   * @param provider - Calendar provider (google, microsoft, apple)
   * @param data - Event data to update
   * @param data.calendarId - Calendar ID where the event exists
   * @param data.eventId - Event ID to update
   * @param data.recurringEventId - Recurring event ID (if editing a recurring event instance)
   * @param data.updateMode - Update mode for recurring events (this, following, all)
   * @param data.title - Updated event title (optional)
   * @param data.description - Updated event description (optional)
   * @param data.start - Updated start date/time in ISO format (optional)
   * @param data.end - Updated end date/time in ISO format (optional)
   * @param data.allDay - Updated all-day flag (optional)
   * @param data.location - Updated event location (optional)
   * @param data.recurrence - Updated recurrence rule (optional)
   * @param data.attendees - Updated list of attendee email addresses (optional)
   * @param data.custom - Updated custom provider-specific properties (optional)
   * @param data.availability - Updated event availability: 'busy' or 'free' (optional)
   * @param data.privacy - Updated event privacy: 'public', 'private', or 'confidential' (optional)
   * @param data.status - Updated event status: 'confirmed', 'tentative', or 'cancelled' (optional)
   * @param options - Optional parameters
   * @param options.accessToken - Access token for authentication (overrides API key)
   *
   * @returns Promise resolving to response with success status and normalized event properties
   *   On success, returns { success: true, provider, id, title, start, end, ...eventProperties, original }
   *   On failure, returns { success: false, message: string }
   *
   * @example
   * // Update a simple event
   * const result = await client.events.update('google', {
   *   calendarId: 'primary',
   *   eventId: 'event-123',
   *   title: 'Updated Meeting Title',
   *   start: '2025-11-10T14:00:00Z',
   *   end: '2025-11-10T15:00:00Z'
   * });
   * // result: {
   * //   success: true,
   * //   provider: 'google',
   * //   id: 'event-123',
   * //   title: 'Updated Meeting Title',
   * //   start: Date,
   * //   end: Date,
   * //   original: <raw Google event object>
   * // }
   *
   * @example
   * // Update only this instance of a recurring event
   * const thisInstance = await client.events.update('microsoft', {
   *   calendarId: 'calendar-id',
   *   eventId: 'event-instance-123',
   *   recurringEventId: 'recurring-event-123',
   *   updateMode: 'this',
   *   title: 'Rescheduled Meeting'
   * });
   *
   * @example
   * // Update all instances in a recurring series
   * const allInstances = await client.events.update('apple', {
   *   calendarId: 'calendar-id',
   *   eventId: 'event-123',
   *   updateMode: 'all',
   *   start: '2025-11-10T10:00:00Z',
   *   end: '2025-11-10T11:00:00Z'
   * });
   *
   * @example
   * // Update this and following instances
   * const following = await client.events.update('google', {
   *   calendarId: 'primary',
   *   eventId: 'event-123',
   *   recurringEventId: 'recurring-123',
   *   updateMode: 'following',
   *   location: 'New Office Location'
   * });
   */
  async update(
    provider: 'google' | 'microsoft' | 'apple',
    data: UpdateEventData,
    options?: { accessToken?: string }
  ): Promise<EventResponse> {
    const headers: Record<string, string> = {};
    if (options?.accessToken) {
      headers['Authorization'] = `Bearer ${options.accessToken}`;
    }
    const bodyWithProvider = { ...data, provider };
    const response = await this.client.put<EventResponse>('/event', bodyWithProvider, { headers });
    return response.data;
  }

  /**
   * Delete a calendar event
   *
   * Deletes an event from the specified calendar for the given provider.
   * Supports deleting single events and recurring event instances with different delete modes.
   *
   * @param params - Delete parameters
   * @param params.provider - Calendar provider (google, microsoft, apple)
   * @param params.calendarId - Calendar ID where the event exists
   * @param params.eventId - Event ID to delete
   * @param params.recurringEventId - Recurring event ID (if deleting a recurring event instance)
   * @param params.deleteMode - Delete mode for recurring events (this, following, all)
   * @param options - Optional parameters
   * @param options.accessToken - Access token for authentication (overrides API key)
   *
   * @returns Promise resolving to delete confirmation
   *
   * @example
   * // Delete a simple event
   * await client.events.delete({
   *   provider: 'google',
   *   calendarId: 'primary',
   *   eventId: 'event-123'
   * });
   *
   * @example
   * // Delete only this instance of a recurring event
   * await client.events.delete({
   *   provider: 'microsoft',
   *   calendarId: 'calendar-id',
   *   eventId: 'event-instance-123',
   *   recurringEventId: 'recurring-event-123',
   *   deleteMode: 'this'
   * });
   *
   * @example
   * // Delete all instances in a recurring series
   * await client.events.delete({
   *   provider: 'apple',
   *   calendarId: 'calendar-id',
   *   eventId: 'event-123',
   *   deleteMode: 'all'
   * });
   *
   * @example
   * // Delete this and following instances
   * await client.events.delete({
   *   provider: 'google',
   *   calendarId: 'primary',
   *   eventId: 'event-123',
   *   recurringEventId: 'recurring-123',
   *   deleteMode: 'following'
   * });
   */
  async delete(
    params: DeleteEventParams,
    options?: { accessToken?: string }
  ): Promise<DeleteEventResponse> {
    const headers: Record<string, string> = {};
    if (options?.accessToken) {
      headers['Authorization'] = `Bearer ${options.accessToken}`;
    }
    const response = await this.client.delete<DeleteEventResponse>('/event', {
      data: params,
      headers,
    });
    return response.data;
  }
}
