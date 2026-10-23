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
import { reviveEventDates } from '../eventDates';

/**
 * Events resource for managing calendar events across multiple providers
 */
export class Events {
  constructor(private readonly client: ApiClient) {}

  /**
   * List calendar events
   *
   * @param params - List parameters (start, end, calendarIds, etc.)
   */
  async list(params?: EventListParams): Promise<EventsListResponse> {
    const queryParams = new URLSearchParams();

    if (params) {
      const { pageSize, start, end, calendarIds, nextPageToken, singleEvents } = params;

      if (pageSize) queryParams.append('pageSize', pageSize.toString());
      if (start) queryParams.append('start', start instanceof Date ? start.toISOString() : start);
      if (end) queryParams.append('end', end instanceof Date ? end.toISOString() : end);
      if (calendarIds) queryParams.append('calendarIds', JSON.stringify(calendarIds));
      if (nextPageToken) queryParams.append('nextPageToken', nextPageToken);
      if (singleEvents !== undefined) queryParams.append('singleEvents', singleEvents.toString());
    }

    const query = queryParams.toString();
    const path = query ? `/events?${query}` : '/events';

    const response = await this.client.get<EventsListResponse>(path);
    const data = response.data || { events: [] };
    return Array.isArray(data.events)
      ? { ...data, events: data.events.map(reviveEventDates) }
      : data;
  }

  /**
   * Create a new calendar event
   *
   * @param params - Event creation parameters including provider
   */
  async create(params: CreateEventData): Promise<EventResponse> {
    const response = await this.client.post<EventResponse>('/event', params);
    return reviveEventDates(response.data);
  }

  /**
   * Update an existing calendar event
   *
   * @param params - Event update parameters including provider
   */
  async update(params: UpdateEventData): Promise<EventResponse> {
    const response = await this.client.put<EventResponse>('/event', params);
    return reviveEventDates(response.data);
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
