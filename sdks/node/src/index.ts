import { ApiClient } from './client';
import { MobiscrollConnectConfig } from './types';
import { Calendars } from './resources/calendars';
import { Events } from './resources/events';
import { Auth } from './resources/auth';

/**
 * Main SDK client for Mobiscroll Connect
 */
export class MobiscrollConnectClient {
  public calendars: Calendars;
  public events: Events;
  public auth: Auth;

  private readonly apiClient: ApiClient;

  constructor(config: MobiscrollConnectConfig) {
    this.apiClient = new ApiClient(config);
    this.calendars = new Calendars(this.apiClient);
    this.events = new Events(this.apiClient);
    this.auth = new Auth(this.apiClient);
  }

  /**
   * Update the API key
   */
  public setApiKey(apiKey: string): void {
    this.apiClient.setApiKey(apiKey);
  }

  /**
   * Get the current configuration
   */
  public getConfig() {
    return this.apiClient.getConfig();
  }
}

export * from './types';
export { ApiClient } from './client';
