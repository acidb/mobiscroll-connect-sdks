import { ApiClient } from '../client';
import { Calendar } from '../types';

/**
 * Calendars resource for managing calendars across multiple providers
 */
export class Calendars {
  constructor(private readonly client: ApiClient) {}

  /**
   * List all calendars from all connected providers
   *
   * @returns Promise resolving to an array of calendars from all providers
   */
  async list(): Promise<Calendar[]> {
    const response = await this.client.get<Calendar[]>('/calendars');
    return response.data || [];
  }
}
