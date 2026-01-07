import { ApiClient } from '../client';
import { Calendar } from '../types';

/**
 * Calendars resource for managing calendars across multiple providers
 */
export class Calendars {
  constructor(private readonly client: ApiClient) {}

  /**
   * List all calendars from all connected providers (Google, Microsoft, Apple)
   *
   * Fetches the list of calendars from all configured calendar providers for the
   * authenticated user. Returns a unified array of calendars across all providers
   * with provider-specific metadata.
   *
   * @param options - Optional parameters
   * @param options.accessToken - Access token for authentication (overrides API key)
   *
   * @returns Promise resolving to an array of calendars from all providers
   *
   * @example
   * // Fetch all calendars for authenticated user
   * const calendars = await client.calendars.list();
   *
   * // Response format:
   * [
   *   {
   *     id: "work@company.com",
   *     title: "My Calendar",
   *     provider: "google",
   *     timeZone: "America/Los_Angeles",
   *     color: "#9fc6e7",
   *     accessRole: "owner"
   *   },
   *   {
   *     id: "AAMkAGI2T...",
   *     title: "Work Calendar",
   *     provider: "microsoft",
   *     timeZone: "UTC",
   *     accessRole: "owner"
   *   },
   *   {
   *     id: "E2857962-EE43-4E90-829C-A826D534C0D9",
   *     title: "Personal",
   *     provider: "apple",
   *     timeZone: "America/New_York"
   *   }
   * ]
   */
  async list(options?: { accessToken?: string }): Promise<Calendar[]> {
    const headers: Record<string, string> = {};
    if (options?.accessToken) {
      headers['Authorization'] = `Bearer ${options.accessToken}`;
    }
    const response = await this.client.get<Calendar[]>('/calendars', { headers });
    return response.data;
  }
}
