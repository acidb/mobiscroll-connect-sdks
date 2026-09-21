import { ApiClient } from '../client';
import {
  SubscribeWebhookParams,
  SubscribeWebhookResponse,
  UnsubscribeWebhookParams,
  UnsubscribeWebhookResponse,
} from '../types';

/**
 * Webhooks resource for subscribing to and unsubscribing from calendar change notifications
 */
export class Webhooks {
  constructor(private readonly client: ApiClient) {}

  /**
   * Subscribe to webhook notifications for a calendar
   *
   * @param params - Subscription parameters including provider and calendar
   * @returns Subscription details, including the channel id needed to unsubscribe later
   *
   * @example
   * ```typescript
   * const subscription = await client.webhooks.subscribeWebhook({
   *   provider: 'google',
   *   calendarId: 'primary',
   * });
   *
   * console.log(subscription.channelId);
   * ```
   */
  async subscribeWebhook(params: SubscribeWebhookParams): Promise<SubscribeWebhookResponse> {
    const response = await this.client.post<SubscribeWebhookResponse>('/subscribe-webhook', params);
    return response.data;
  }

  /**
   * Unsubscribe from webhook notifications for a calendar
   *
   * @param params - Unsubscription parameters including provider and channel id
   * @returns Success confirmation. Returns `success: true` even if the provider-side
   * subscription had already expired — check `message` for details in that case.
   *
   * @example
   * ```typescript
   * await client.webhooks.unsubscribeWebhook({
   *   provider: 'google',
   *   channelId: subscription.channelId,
   *   resourceId: subscription.subscription.resourceId,
   * });
   * ```
   */
  async unsubscribeWebhook(params: UnsubscribeWebhookParams): Promise<UnsubscribeWebhookResponse> {
    const response = await this.client.post<UnsubscribeWebhookResponse>(
      '/unsubscribe-webhook',
      params
    );
    return response.data;
  }
}
