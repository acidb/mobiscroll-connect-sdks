import { ApiClient } from '../client';
import {
  SubscribeWebhookParams,
  SubscribeWebhookResponse,
  UnsubscribeWebhookParams,
  UnsubscribeWebhookResponse,
  WebhookDelivery,
  WebhookHeaders,
  WebhookVerificationError,
} from '../types';
import { WEBHOOK_KEYS_PATH, WebhookKeyStore, verifyWebhookSignature } from '../webhookVerification';

/**
 * Webhooks resource for calendar change notifications: subscribe, unsubscribe, and verify deliveries
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

  /**
   * Verify that a webhook delivery came from Mobiscroll Connect, and parse it
   *
   * Fetches the public keys from `/.well-known/webhook-keys` on first use and caches them for the
   * whole process, refreshing them as the endpoint's `Cache-Control` allows. When no signature
   * matches, it re-fetches the keys once (at most once a minute) before rejecting, so a key
   * rotation never rejects genuine deliveries. `webhookPublicKey` from the config is used only
   * when the endpoint cannot be reached.
   *
   * @param payload - The raw request body, exactly as received; not a parsed object
   * @param headers - The request headers
   * @returns The parsed delivery
   * @throws {WebhookVerificationError} When the delivery is not genuine; respond with a 4xx
   *
   * @example
   * ```typescript
   * app.post('/webhooks/mobiscroll', express.raw({ type: 'application/json' }), async (req, res) => {
   *   let delivery;
   *   try {
   *     delivery = await client.webhooks.verifyWebhook(req.body, req.headers);
   *   } catch (error) {
   *     if (!(error instanceof WebhookVerificationError)) throw error;
   *     return res.sendStatus(error.reason === 'no_public_keys' ? 503 : 401);
   *   }
   *   res.sendStatus(204);
   *   handleDelivery(delivery);
   * });
   * ```
   */
  async verifyWebhook(
    payload: string | Uint8Array,
    headers: WebhookHeaders
  ): Promise<WebhookDelivery> {
    const store = WebhookKeyStore.forUrl(
      new URL(WEBHOOK_KEYS_PATH, this.client.baseURL).toString()
    );
    const pinnedKey = this.client.getConfig().webhookPublicKey;
    const keysOrPinned = (keys: string[]) => (keys.length > 0 || !pinnedKey ? keys : [pinnedKey]);

    try {
      verifyWebhookSignature(payload, headers, keysOrPinned(await store.getKeys()));
    } catch (error) {
      const retryable =
        error instanceof WebhookVerificationError &&
        (error.reason === 'no_matching_signature' || error.reason === 'no_public_keys');
      if (!retryable || !store.canRefetch()) {
        throw error;
      }
      await store.refresh();
      verifyWebhookSignature(payload, headers, keysOrPinned(await store.getKeys()));
    }

    const body = typeof payload === 'string' ? payload : Buffer.from(payload).toString('utf8');
    let delivery: unknown;
    try {
      delivery = JSON.parse(body);
    } catch {
      throw new WebhookVerificationError('Webhook payload is not valid JSON', 'invalid_payload');
    }
    if (typeof delivery !== 'object' || delivery === null || Array.isArray(delivery)) {
      throw new WebhookVerificationError('Webhook payload is not a JSON object', 'invalid_payload');
    }
    return delivery as WebhookDelivery;
  }
}
