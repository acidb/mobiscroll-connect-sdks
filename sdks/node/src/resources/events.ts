import { ApiClient } from '../client';
import {
  DeleteEventParams,
  DeleteEventResponse,
  EventListParams,
  EventResponse,
  EventsListResponse,
  UpdateEventData,
  CreateEventData,
} from '../types';

/**
 * Events resource for managing calendar events across multiple providers
 */
export class Events {
  constructor(private readonly client: ApiClient) {}

  /**
   * List calendar events
   *
   * `singleEvents` (default `true`) expands recurring events into occurrences; `false` returns the
   * series masters. `includeCancelled` also returns deleted occurrences, as events with
   * `status: 'cancelled'`, with `singleEvents: true`. `eventIds` returns only the given events,
   * at most 40 ids, and cannot be combined with `calendarIds`. `info` on the response carries the
   * providers that failed and the calendars no connected account can read.
   *
   * @param params - List parameters (start, end, calendarIds, eventIds, singleEvents, includeCancelled, etc.)
   * @returns The events of this page, the page size, `nextPageToken` when more events follow, and `info` when set
   *
   * @example
   * ```typescript
   * // Fetch two events by id, each as the event itself (a series' master, or an occurrence)
   * const result = await client.events.list({
   *   eventIds: { google: { 'ana@example.com': ['abc123', 'def456'] } },
   *   singleEvents: false,
   * });
   * ```
   */
  async list(params?: EventListParams): Promise<EventsListResponse> {
    const queryParams = new URLSearchParams();

    if (params) {
      const {
        pageSize,
        start,
        end,
        calendarIds,
        nextPageToken,
        singleEvents,
        includeCancelled,
        eventIds,
      } = params;

      if (pageSize) queryParams.append('pageSize', pageSize.toString());
      if (start) queryParams.append('start', start instanceof Date ? start.toISOString() : start);
      if (end) queryParams.append('end', end instanceof Date ? end.toISOString() : end);
      if (calendarIds) queryParams.append('calendarIds', JSON.stringify(calendarIds));
      if (nextPageToken) queryParams.append('nextPageToken', nextPageToken);
      if (singleEvents !== undefined) queryParams.append('singleEvents', singleEvents.toString());
      if (includeCancelled !== undefined)
        queryParams.append('includeCancelled', includeCancelled.toString());
      if (eventIds) queryParams.append('eventIds', JSON.stringify(eventIds));
    }

    const query = queryParams.toString();
    const path = query ? `/events?${query}` : '/events';

    const response = await this.client.get<EventsListResponse>(path);
    return response.data || { events: [] };
  }

  /**
   * Create a new calendar event
   *
   * @param params - Event creation parameters including provider
   */
  async create(params: CreateEventData): Promise<EventResponse> {
    const response = await this.client.post<EventResponse>('/event', params);
    return response.data;
  }

  /**
   * Update an existing calendar event
   *
   * @param params - Event update parameters including provider
   */
  async update(params: UpdateEventData): Promise<EventResponse> {
    const response = await this.client.put<EventResponse>('/event', params);
    return response.data;
  }

  /**
   * Delete a calendar event
   *
   * @param params - Event deletion parameters including provider
   */
  async delete(params: DeleteEventParams): Promise<DeleteEventResponse> {
    const queryParams = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined) {
        queryParams.append(key, value.toString());
      }
    });

    const query = queryParams.toString();
    const path = query ? `/event?${query}` : '/event';

    const response = await this.client.delete<DeleteEventResponse>(path);
    return response.data;
  }
}
