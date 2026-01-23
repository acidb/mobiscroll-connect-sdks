import { ApiClient } from './client';
import { MobiscrollConnectConfig, TokenResponse } from './types';
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
   * Set credentials for authentication
   * @param tokens - The tokens to use
   */
  public setCredentials(tokens: TokenResponse): void {
    this.apiClient.setCredentials(tokens);
  }

  /**
   * Listen to events
   */
  public on(event: string, listener: (...args: any[]) => void): this {
    this.apiClient.on(event, listener);
    return this;
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
