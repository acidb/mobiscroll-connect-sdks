import { ApiClient } from '../client';
import {
  DeleteEventParams,
  DeleteEventResponse,
  EventListParams,
  EventResponse,
  EventsListResponse,
  UpdateEventData,
  CreateEventData,
  ProviderName,
} from '../types';

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
      const { pageSize, start, end, calendarIds, nextPageToken, appleToken, singleEvents } = params;

      if (pageSize) queryParams.append('pageSize', pageSize.toString());
      if (start) queryParams.append('start', start instanceof Date ? start.toISOString() : start);
      if (end) queryParams.append('end', end instanceof Date ? end.toISOString() : end);
      if (calendarIds) queryParams.append('calendarIds', JSON.stringify(calendarIds));
      if (nextPageToken) queryParams.append('nextPageToken', nextPageToken);
      if (appleToken) queryParams.append('appleToken', JSON.stringify(appleToken));
      if (singleEvents !== undefined) queryParams.append('singleEvents', singleEvents.toString());
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
  async create(params: CreateEventData & { provider: ProviderName }): Promise<EventResponse> {
    const response = await this.client.post<EventResponse>('/event', params);
    return response.data;
  }

  /**
   * Update an existing calendar event
   *
   * @param params - Event update parameters including provider
   */
  async update(params: UpdateEventData & { provider: ProviderName }): Promise<EventResponse> {
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
