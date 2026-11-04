import axios from 'axios';
import {
  MobiscrollConnectClient,
  WebhookVerificationError,
  verifyWebhookSignature,
} from '../index';
import { WebhookKeyStore } from '../webhookVerification';
import vectors from './fixtures/webhook-vectors.json';

type Vector = (typeof vectors.cases)[number];

const byName = (name: string): Vector => {
  const vector = vectors.cases.find((candidate) => candidate.name === name);
  if (!vector) throw new Error(`Missing vector: ${name}`);
  return vector;
};

const KEYS_URL = 'https://connect.mobiscroll.com/.well-known/webhook-keys';

describe('verifyWebhookSignature', () => {
  it.each(vectors.cases.map((vector) => [vector.name, vector] as const))('%s', (_name, vector) => {
    const run = () =>
      verifyWebhookSignature(
        vector.body,
        vector.headers as Record<string, string>,
        vector.publicKeys,
        {
          now: vector.now,
        }
      );
    if (vector.valid) {
      expect(run).not.toThrow();
    } else {
      expect(run).toThrow(WebhookVerificationError);
    }
  });

  it('accepts a Buffer body and header names in any casing', () => {
    const vector = byName('valid single signature');
    const headers = Object.fromEntries(
      Object.entries(vector.headers).map(([key, value]) => [key.toUpperCase(), [value]])
    );
    expect(() =>
      verifyWebhookSignature(Buffer.from(vector.body), headers, vector.publicKeys, {
        now: vector.now,
      })
    ).not.toThrow();
  });

  it('accepts a Fetch API Headers object', () => {
    const vector = byName('valid single signature');
    expect(() =>
      verifyWebhookSignature(vector.body, new Headers(vector.headers), vector.publicKeys, {
        now: vector.now,
      })
    ).not.toThrow();
  });

  it('rejects a parsed body with a clear reason', () => {
    const vector = byName('valid single signature');
    expect(() =>
      verifyWebhookSignature(JSON.parse(vector.body), vector.headers, vector.publicKeys, {
        now: vector.now,
      })
    ).toThrow(expect.objectContaining({ reason: 'invalid_payload' }));
  });

  it('reports why verification failed', () => {
    const stale = byName('timestamp 301 s old, stale');
    expect(() =>
      verifyWebhookSignature(stale.body, stale.headers, stale.publicKeys, { now: stale.now })
    ).toThrow(
      expect.objectContaining({
        reason: 'timestamp_out_of_tolerance',
        code: 'WEBHOOK_VERIFICATION_ERROR',
      })
    );
  });
});

describe('Webhooks.verifyWebhook', () => {
  const valid = byName('valid single signature');
  const keysResponse = (keys: string[], cacheControl = 'public, max-age=3600') => ({
    data: { keys: keys.map((key) => ({ id: key, alg: 'ed25519', key, status: 'active' })) },
    headers: { 'cache-control': cacheControl },
  });

  let get: jest.SpyInstance;
  let clock: number;

  const createClient = (webhookPublicKey?: string) =>
    new MobiscrollConnectClient({
      clientId: 'id',
      clientSecret: 'secret',
      redirectUri: 'uri',
      webhookPublicKey,
    });

  beforeEach(() => {
    WebhookKeyStore.reset();
    clock = valid.now * 1000;
    jest.spyOn(Date, 'now').mockImplementation(() => clock);
    get = jest.spyOn(axios, 'get');
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('fetches the keys lazily from the origin of the API base URL and returns the parsed delivery', async () => {
    get.mockResolvedValue(keysResponse([vectors.keys.active]));
    const client = createClient();
    expect(get).not.toHaveBeenCalled();

    const delivery = await client.webhooks.verifyWebhook(valid.body, valid.headers);

    expect(get).toHaveBeenCalledWith(
      KEYS_URL,
      expect.objectContaining({ timeout: expect.any(Number) })
    );
    expect(delivery.events[0].title).toBe('Café meeting ☕ — Zoë');
    expect(delivery.userId).toBe('user_42');
  });

  it('parses a delivery with a conference object and keeps start/end as ISO strings', async () => {
    get.mockResolvedValue(keysResponse([vectors.keys.active]));
    const vector = byName('delivery: Google events with and without a conference');

    const delivery = await createClient().webhooks.verifyWebhook(vector.body, vector.headers);

    const [meeting, lunch] = delivery.events;
    // The raw object as delivered; declared `string` like CalendarEvent.conference.
    expect(meeting.conference).toEqual({
      url: 'https://meet.google.com/abc-defg-hij',
      provider: 'hangoutsMeet',
      meetingProvider: 'hangoutsMeet',
      data: expect.objectContaining({ conferenceId: 'abc-defg-hij' }),
    });
    expect(meeting.start).toBe('2026-09-22T09:00:00.000Z');
    expect(meeting.end).toBe('2026-09-22T10:00:00.000Z');
    expect(meeting.lastModified).toBe('2026-09-21T09:58:12.000Z');
    expect(meeting.changeType).toBe('updated');
    expect(lunch.conference).toBeUndefined();
    expect(lunch.allDay).toBe(true);
    expect(delivery.metadata).toEqual({
      channelId: 'chan_google_1',
      eventCount: 2,
      isInitialSync: false,
    });
  });

  it('parses a deleted placeholder without calendarId or original', async () => {
    get.mockResolvedValue(keysResponse([vectors.keys.active]));
    const vector = byName('delivery: Microsoft Teams event and a deleted placeholder');

    const delivery = await createClient().webhooks.verifyWebhook(vector.body, vector.headers);

    const [teams, deleted] = delivery.events;
    expect((teams.conference as unknown as { provider: string }).provider).toBe('teams');
    expect(deleted.changeType).toBe('deleted');
    expect(deleted.calendarId).toBeUndefined();
    expect(deleted.original).toBeUndefined();
    expect(typeof deleted.start).toBe('string');
  });

  it('passes the API 1.6.1 conferenceData through and keeps calendarId on deleted events', async () => {
    get.mockResolvedValue(keysResponse([vectors.keys.active]));
    const verify = async (name: string) => {
      const vector = byName(name);
      return createClient().webhooks.verifyWebhook(vector.body, vector.headers);
    };

    const google = await verify(
      'delivery: 1.6.1 Google Meet event with conferenceData and a cancelled event with calendarId'
    );
    const [meet, cancelled] = google.events;
    expect(meet.conferenceData).toEqual(
      expect.objectContaining({
        provider: 'hangoutsMeet',
        url: 'https://meet.google.com/abc-defg-hij',
        original: expect.objectContaining({ conferenceId: 'abc-defg-hij' }),
        conferenceId: 'abc-defg-hij',
      })
    );
    expect(cancelled.calendarId).toBe('work@company.com');
    expect(cancelled.status).toBe('cancelled');

    const microsoft = await verify(
      'delivery: 1.6.1 Microsoft Teams event with conferenceData and a deleted placeholder with calendarId'
    );
    const [teams, deleted] = microsoft.events;
    expect(teams.conferenceData).toEqual(
      expect.objectContaining({
        provider: 'teams',
        url: 'https://teams.microsoft.com/l/meetup-join/19%3ameeting_abc',
        original: { joinUrl: expect.any(String), conferenceId: '123456789' },
      })
    );
    expect(deleted.calendarId).toBe('AAMkAGI2Y2FsAAA=');
    expect(deleted.original).toBeUndefined();

    const apple = await verify(
      'delivery: 1.6.1 Apple event with a URL as conference and a deleted placeholder with calendarId'
    );
    const [booking, removed] = apple.events;
    expect(booking.conferenceData).toEqual({
      provider: 'caldav',
      url: 'https://example.com/room/42',
    });
    expect(removed.calendarId).toBe('https://caldav.icloud.com/1234567/calendars/work/');
  });

  it('shares one key cache across client instances and honours max-age', async () => {
    get.mockResolvedValue(keysResponse([vectors.keys.active], 'max-age=120'));

    await createClient().webhooks.verifyWebhook(valid.body, valid.headers);
    await createClient().webhooks.verifyWebhook(valid.body, valid.headers);
    expect(get).toHaveBeenCalledTimes(1);

    clock += 121 * 1000;
    await createClient().webhooks.verifyWebhook(valid.body, valid.headers);
    expect(get).toHaveBeenCalledTimes(2);
  });

  it('re-fetches once after a rotation and accepts the delivery', async () => {
    get
      .mockResolvedValueOnce(keysResponse([vectors.keys.unrelated]))
      .mockResolvedValueOnce(keysResponse([vectors.keys.active]));
    clock -= 61 * 1000;
    await WebhookKeyStore.forUrl(KEYS_URL).getKeys();
    clock += 61 * 1000;

    const delivery = await createClient().webhooks.verifyWebhook(valid.body, valid.headers);

    expect(delivery.calendarId).toBe('primary');
    expect(get).toHaveBeenCalledTimes(2);
  });

  it('does not re-fetch more than once a minute on forged deliveries', async () => {
    get.mockResolvedValue(keysResponse([vectors.keys.active]));
    const client = createClient();
    const forged = byName('tampered body');

    await expect(client.webhooks.verifyWebhook(forged.body, forged.headers)).rejects.toThrow(
      WebhookVerificationError
    );
    await expect(client.webhooks.verifyWebhook(forged.body, forged.headers)).rejects.toThrow(
      WebhookVerificationError
    );
    expect(get).toHaveBeenCalledTimes(1);
  });

  it('falls back to the pinned key when the endpoint cannot be reached', async () => {
    get.mockRejectedValue(new Error('ECONNREFUSED'));
    const delivery = await createClient(vectors.keys.active).webhooks.verifyWebhook(
      valid.body,
      valid.headers
    );
    expect(delivery.provider).toBe('google');
  });

  it('ignores the pinned key while fetched keys are available', async () => {
    get.mockResolvedValue(keysResponse([vectors.keys.unrelated]));
    await expect(
      createClient(vectors.keys.active).webhooks.verifyWebhook(valid.body, valid.headers)
    ).rejects.toThrow(expect.objectContaining({ reason: 'no_matching_signature' }));
  });

  it('keeps the last good keys when a refresh fails', async () => {
    get.mockResolvedValueOnce(keysResponse([vectors.keys.active]));
    const client = createClient();
    await client.webhooks.verifyWebhook(valid.body, valid.headers);

    get.mockRejectedValue(new Error('timeout'));
    clock += 3601 * 1000;
    const later = { ...valid.headers, 'webhook-timestamp': String(valid.now + 3601) };
    await expect(client.webhooks.verifyWebhook(valid.body, later)).rejects.toThrow(
      expect.objectContaining({ reason: 'no_matching_signature' })
    );
    expect(get).toHaveBeenCalledTimes(2);
  });

  it('fails with no_public_keys when nothing can be fetched and no key is pinned', async () => {
    get.mockRejectedValue(new Error('ECONNREFUSED'));
    await expect(createClient().webhooks.verifyWebhook(valid.body, valid.headers)).rejects.toThrow(
      expect.objectContaining({ reason: 'no_public_keys' })
    );
  });

  it('deduplicates concurrent key fetches', async () => {
    get.mockResolvedValue(keysResponse([vectors.keys.active]));
    const client = createClient();
    await Promise.all(
      [1, 2, 3].map(() => client.webhooks.verifyWebhook(valid.body, valid.headers))
    );
    expect(get).toHaveBeenCalledTimes(1);
  });
});
