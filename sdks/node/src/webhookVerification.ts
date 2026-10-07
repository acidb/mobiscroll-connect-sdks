import { createPublicKey, verify, KeyObject } from 'crypto';
import axios from 'axios';
import { WebhookHeaders, WebhookVerificationError, VerifyWebhookSignatureOptions } from './types';

export const WEBHOOK_TOLERANCE_SECONDS = 300;
export const WEBHOOK_KEYS_PATH = '/.well-known/webhook-keys';

const PUBLIC_KEY_PREFIX = 'whpk_';
const SIGNATURE_VERSION = 'v1a';
const DEFAULT_KEYS_MAX_AGE_MS = 60 * 60 * 1000;
const MAX_KEYS_MAX_AGE_MS = 24 * 60 * 60 * 1000;
const MIN_REFETCH_INTERVAL_MS = 60 * 1000;
const KEYS_FETCH_TIMEOUT_MS = 10 * 1000;

function readHeader(headers: WebhookHeaders, name: string): string | undefined {
  if (typeof (headers as { get?: unknown }).get === 'function') {
    return (headers as { get(name: string): string | null }).get(name) ?? undefined;
  }
  const record = headers as Record<string, string | string[] | undefined>;
  const key = Object.keys(record).find((candidate) => candidate.toLowerCase() === name);
  const value = key === undefined ? undefined : record[key];
  return Array.isArray(value) ? value[0] : value;
}

function toBuffer(payload: string | Uint8Array): Buffer {
  if (typeof payload === 'string') {
    return Buffer.from(payload, 'utf8');
  }
  if (payload instanceof Uint8Array) {
    return Buffer.from(payload.buffer, payload.byteOffset, payload.byteLength);
  }
  throw new WebhookVerificationError(
    'Pass the raw request body as a string or Buffer, not a parsed object',
    'invalid_payload'
  );
}

function parsePublicKey(value: string): KeyObject | null {
  if (typeof value !== 'string') {
    return null;
  }
  const raw = Buffer.from(value.trim().replace(/^whpk_/, ''), 'base64');
  if (raw.length !== 32) {
    return null;
  }
  try {
    return createPublicKey({
      key: { kty: 'OKP', crv: 'Ed25519', x: raw.toString('base64url') },
      format: 'jwk',
    });
  } catch {
    return null;
  }
}

/**
 * Verifies the Standard Webhooks `v1a` (Ed25519) signature of a Mobiscroll Connect delivery
 * against the given public keys, without fetching anything.
 *
 * Use it with a pinned key in handlers that cannot make outbound requests. Otherwise prefer
 * `client.webhooks.verifyWebhook()`, which fetches and refreshes the keys for you.
 *
 * @param payload - The raw request body, exactly as received
 * @param headers - The request headers; must include `webhook-id`, `webhook-timestamp` and `webhook-signature`
 * @param publicKeys - `whpk_` public keys; the delivery is genuine if any signature verifies against any key
 * @param options - Timestamp tolerance (default 300 s) and the clock to check it against
 * @throws {WebhookVerificationError} When the delivery is not genuine or cannot be checked
 *
 * @example
 * ```typescript
 * verifyWebhookSignature(req.body, req.headers, [process.env.MOBISCROLL_WEBHOOK_PUBLIC_KEY!]);
 * ```
 */
export function verifyWebhookSignature(
  payload: string | Uint8Array,
  headers: WebhookHeaders,
  publicKeys: readonly string[],
  options: VerifyWebhookSignatureOptions = {}
): void {
  const body = toBuffer(payload);
  const id = readHeader(headers, 'webhook-id');
  const timestamp = readHeader(headers, 'webhook-timestamp');
  const signatureHeader = readHeader(headers, 'webhook-signature');
  if (!id || !timestamp || !signatureHeader) {
    throw new WebhookVerificationError(
      'Missing webhook-id, webhook-timestamp or webhook-signature header',
      'missing_headers'
    );
  }

  if (!/^\d+$/.test(timestamp)) {
    throw new WebhookVerificationError('Invalid webhook-timestamp header', 'invalid_timestamp');
  }
  const tolerance = options.toleranceSeconds ?? WEBHOOK_TOLERANCE_SECONDS;
  const now = options.now ?? Math.floor(Date.now() / 1000);
  if (Math.abs(now - Number(timestamp)) > tolerance) {
    throw new WebhookVerificationError(
      'Webhook timestamp is outside the allowed tolerance',
      'timestamp_out_of_tolerance'
    );
  }

  const keys = publicKeys.map(parsePublicKey).filter((key): key is KeyObject => key !== null);
  if (keys.length === 0) {
    throw new WebhookVerificationError('No valid webhook public keys available', 'no_public_keys');
  }

  const signedContent = Buffer.concat([Buffer.from(`${id}.${timestamp}.`, 'utf8'), body]);
  const matched = signatureHeader.split(' ').some((entry) => {
    const separator = entry.indexOf(',');
    if (separator === -1 || entry.slice(0, separator) !== SIGNATURE_VERSION) {
      return false;
    }
    const signature = Buffer.from(entry.slice(separator + 1), 'base64');
    if (signature.length !== 64) {
      return false;
    }
    return keys.some((key) => {
      try {
        return verify(null, signedContent, key, signature);
      } catch {
        return false;
      }
    });
  });

  if (!matched) {
    throw new WebhookVerificationError('No webhook signature matched', 'no_matching_signature');
  }
}

function parseMaxAge(cacheControl: unknown): number {
  const match = typeof cacheControl === 'string' ? /max-age=(\d+)/i.exec(cacheControl) : null;
  if (!match) {
    return DEFAULT_KEYS_MAX_AGE_MS;
  }
  return Math.min(Number(match[1]) * 1000, MAX_KEYS_MAX_AGE_MS);
}

/**
 * Process-wide cache of the keys published at one keys URL, shared by every client instance.
 * Follows the endpoint's `Cache-Control`, re-fetches at most once a minute, and keeps the last
 * good keys when a fetch fails.
 */
export class WebhookKeyStore {
  private static readonly stores = new Map<string, WebhookKeyStore>();

  private keys: string[] = [];
  private fetchedAt = 0;
  private lastAttemptAt = 0;
  private maxAgeMs = DEFAULT_KEYS_MAX_AGE_MS;
  private inFlight: Promise<void> | null = null;

  private constructor(private readonly url: string) {}

  static forUrl(url: string): WebhookKeyStore {
    let store = WebhookKeyStore.stores.get(url);
    if (!store) {
      store = new WebhookKeyStore(url);
      WebhookKeyStore.stores.set(url, store);
    }
    return store;
  }

  /** @internal Clears every cached key set; for tests. */
  static reset(): void {
    WebhookKeyStore.stores.clear();
  }

  async getKeys(): Promise<string[]> {
    const now = Date.now();
    if (this.inFlight) {
      await this.inFlight;
    } else if (now - this.fetchedAt > this.maxAgeMs && this.canRefetch(now)) {
      await this.refresh();
    }
    return this.keys;
  }

  canRefetch(now: number = Date.now()): boolean {
    return now - this.lastAttemptAt >= MIN_REFETCH_INTERVAL_MS;
  }

  refresh(): Promise<void> {
    if (!this.inFlight) {
      this.inFlight = this.fetchKeys().finally(() => {
        this.inFlight = null;
      });
    }
    return this.inFlight;
  }

  private async fetchKeys(): Promise<void> {
    this.lastAttemptAt = Date.now();
    try {
      const response = await axios.get<{ keys?: Array<{ alg?: string; key?: string }> }>(this.url, {
        timeout: KEYS_FETCH_TIMEOUT_MS,
      });
      const keys = (response.data?.keys ?? [])
        .filter(
          (entry) => typeof entry?.key === 'string' && entry.key.startsWith(PUBLIC_KEY_PREFIX)
        )
        .filter((entry) => !entry.alg || entry.alg.toLowerCase() === 'ed25519')
        .map((entry) => entry.key as string);
      if (keys.length > 0) {
        this.keys = keys;
        this.fetchedAt = Date.now();
        this.maxAgeMs = parseMaxAge(response.headers?.['cache-control']);
      }
    } catch {
      // Keep the last good keys; the caller falls back to a pinned key when there are none.
    }
  }
}
