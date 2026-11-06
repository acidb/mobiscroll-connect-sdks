import type { calendar_v3 } from 'googleapis';
import type { DAVCalendar } from 'tsdav';
import type { VEvent } from 'node-ical';
import type { Event as MicrosoftGraphEvent } from '@microsoft/microsoft-graph-types';

export type MicrosoftTokens = {
  access_token: string;
  refresh_token?: string;
  expires_in?: number;
  expires_at?: number;
  id_token?: string;
  ext_expires_in?: string | number;
  scope?: string;
  token_type?: string;
  [key: string]: unknown;
};

export type AppleCredentials = {
  username: string;
  password: string;
};

export type CalDavCredentials = {
  username: string;
  password: string;
  serverUrl: string;
};

/**
 * Configuration options for the Mobiscroll Connect client
 */
export interface MobiscrollConnectConfig {
  /**
   * Client ID for OAuth authentication
   */
  clientId: string;

  /**
   * Client Secret for OAuth authentication
   */
  clientSecret: string;

  /**
   * Redirect URI for OAuth authentication
   */
  redirectUri: string;

  /**
   * Pinned `whpk_` webhook public key. Used by `webhooks.verifyWebhook()` only when the keys
   * endpoint cannot be reached; it stops verifying once Mobiscroll retires that key.
   */
  webhookPublicKey?: string;
}

export interface ApiResponse<T = unknown> {
  data: T;
  status: number;
  headers: Record<string, string>;
}

export interface ApiErrorResponse {
  message: string;
  code?: string;
  details?: unknown;
  /** Present on a `calendar_permission_required` 403. See {@link CalendarPermissionError}. */
  accounts?: BlockedAccount[];
}

export class MobiscrollConnectError extends Error {
  constructor(
    message: string,
    public code?: string
  ) {
    super(message);
    this.name = 'MobiscrollConnectError';
  }
}

export class AuthenticationError extends MobiscrollConnectError {
  constructor(message: string) {
    super(message, 'AUTHENTICATION_ERROR');
    this.name = 'AuthenticationError';
  }
}

/**
 * A connected account that withheld calendar access on its provider's consent screen.
 */
export interface BlockedAccount {
  provider: 'google' | 'microsoft' | 'apple' | 'caldav';
  account: string;
}

/**
 * Raised when no connected account has the calendar access the request needs.
 *
 * The user completed sign-in but did not grant the calendar permission — Google's consent
 * screen presents it as a separate checkbox. This cannot be repaired server-side, because
 * providers only issue permissions at consent time: the accounts in {@link accounts} have
 * to run the connect flow again and allow calendar access.
 *
 * Extends {@link AuthenticationError}, so existing handlers keep working unchanged.
 */
export class CalendarPermissionError extends AuthenticationError {
  constructor(
    message: string,
    public accounts: BlockedAccount[] = []
  ) {
    super(message);
    this.name = 'CalendarPermissionError';
    this.code = 'CALENDAR_PERMISSION_REQUIRED';
  }
}

export class NotFoundError extends MobiscrollConnectError {
  constructor(message: string) {
    super(message, 'NOT_FOUND_ERROR');
    this.name = 'NotFoundError';
  }
}

export class ValidationError extends MobiscrollConnectError {
  constructor(
    message: string,
    public details?: unknown
  ) {
    super(message, 'VALIDATION_ERROR');
    this.name = 'ValidationError';
  }
}

export class RateLimitError extends MobiscrollConnectError {
  constructor(
    message: string,
    public retryAfter?: number
  ) {
    super(message, 'RATE_LIMIT_ERROR');
    this.name = 'RateLimitError';
  }
}

export class ServerError extends MobiscrollConnectError {
  constructor(
    message: string,
    public status: number
  ) {
    super(message, 'SERVER_ERROR');
    this.name = 'ServerError';
  }
}

export class NetworkError extends MobiscrollConnectError {
  constructor(message: string) {
    super(message, 'NETWORK_ERROR');
    this.name = 'NetworkError';
  }
}

export enum ProviderEnum {
  Google = 'google',
  Microsoft = 'microsoft',
  Apple = 'apple',
  CalDav = 'caldav',
}

export type ProviderName =
  | ProviderEnum.Google
  | ProviderEnum.Microsoft
  | ProviderEnum.Apple
  | ProviderEnum.CalDav;

export const ProviderNames: ProviderName[] = [
  ProviderEnum.Google,
  ProviderEnum.Microsoft,
  ProviderEnum.Apple,
  ProviderEnum.CalDav,
];

export type Calendar = {
  provider: ProviderName;
  /** The connected account this calendar belongs to. */
  account: string;
  /** `true` when the authenticated connection cannot write to this calendar. */
  readOnly: boolean;
  id: string;
  title: string;
  timeZone: string;
  color: string;
  description: string;
  original:
    | calendar_v3.Schema$CalendarListEntry
    | { id?: string; name?: string; [key: string]: unknown }
    | DAVCalendar;
};

export type EventAttendee = {
  email: string;
  status: 'accepted' | 'declined' | 'tentative' | 'none';
  organizer?: boolean;
};

export type CalendarEventAvailability = 'busy' | 'free';
export type CalendarEventPrivacy = 'public' | 'private' | 'confidential';
export type CalendarEventStatus = 'confirmed' | 'tentative' | 'cancelled';

/**
 * The conference of an event, with the same fields on every provider. The provider's own conference
 * data (Google's `conferenceId`/`entryPoints`, Microsoft's `joinUrl`/`conferenceId`) is in `original`.
 */
export interface ConferenceData {
  /**
   * Conference system identifier. Google: the type Google reports (`hangoutsMeet`, `addOn`, or the legacy
   * `eventHangout`, `eventNamedHangout`), or `google-meet` when it reports none. Microsoft: `teams`.
   * Apple, CalDAV: `caldav`. Ignored on create and update.
   */
  provider?: string;
  /** The join URL, the same value as the event's `conference`. Returned only; ignored on create and update. */
  url?: string;
  /**
   * The conference data exactly as the calendar provider returns it: Google's `conferenceData`, Microsoft's
   * `onlineMeeting`. Absent for Apple and CalDAV, and for Microsoft when the event has no `onlineMeeting`.
   * Returned only; ignored on create and update.
   */
  original?: Record<string, unknown>;
  /**
   * On create or update, `true` generates a meeting link: Google Meet on Google, Teams on Microsoft. Google
   * then ignores `conference`; Microsoft never writes `conference`; Apple and CalDAV ignore `autoGenerate`.
   */
  autoGenerate?: boolean;
}

export interface CalendarEvent {
  provider: ProviderName;
  id: string;
  calendarId: string;
  title: string;
  /** Event description or notes. */
  description?: string;
  /** ISO 8601 date-time string, as returned by the API. */
  start: string;
  /** ISO 8601 date-time string, as returned by the API. */
  end: string;
  allDay: boolean;
  recurringEventId?: string;
  color?: string;
  location?: string;
  attendees?: EventAttendee[];
  custom?: Record<string, unknown>;
  /** The join URL of the event's conference. */
  conference?: string;
  /** The conference details; see {@link ConferenceData}. */
  conferenceData?: ConferenceData;
  availability?: CalendarEventAvailability;
  privacy?: CalendarEventPrivacy;
  status?: CalendarEventStatus;
  /**
   * ISO 8601 timestamp of the last modification, e.g. `2026-03-10T13:36:08.000Z`. For a deleted event in a
   * webhook delivery, when the deletion was detected.
   */
  lastModified?: string;
  link?: string;
  /** Provider-native event. Absent on deleted Microsoft, Apple and CalDAV events in webhook deliveries. */
  original?: calendar_v3.Schema$Event | MicrosoftGraphEvent | VEvent;
  /**
   * Set only on events in a webhook delivery: what happened to the event. Deleted Microsoft, Apple and
   * CalDAV events arrive as placeholders; rely only on `id`, `provider`, `calendarId` and `changeType` for them.
   */
  changeType?: 'created' | 'updated' | 'deleted';
}

/**
 * An event in a webhook delivery.
 *
 * @deprecated Use {@link CalendarEvent}, which has `changeType` for webhook deliveries. `WebhookEvent`
 * will be removed in 2.0.0.
 */
export type WebhookEvent = CalendarEvent;

export type RecurrenceFrequency = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY';

export interface RecurrenceRule {
  frequency: RecurrenceFrequency;
  interval?: number;
  count?: number;
  until?: string;
  byDay?: string[];
  byMonthDay?: number[];
  byMonth?: number[];
}

export type RecurrenceUpdateMode = 'this' | 'following' | 'all';

export interface EventCreateData {
  provider: ProviderName;
  calendarId: string;
  title: string;
  start: Date | string;
  end: Date | string;
  description?: string;
  location?: string;
  allDay?: boolean;
  attendees?: string[];
  recurrence?: RecurrenceRule;
  custom?: Record<string, unknown>;
  /** Conference meeting URL to attach to the event. */
  conference?: string;
  /** Conference options, e.g. `{ autoGenerate: true }` to create a provider meeting link. */
  conferenceData?: ConferenceData;
  availability?: CalendarEventAvailability;
  privacy?: CalendarEventPrivacy;
  status?: CalendarEventStatus;
}

export interface EventUpdateData extends Partial<EventCreateData> {
  eventId: string;
  recurringEventId?: string;
  updateMode?: RecurrenceUpdateMode;
}

export type CreateEventData = EventCreateData;
export type UpdateEventData = EventUpdateData;

export interface EventDeleteData {
  calendarId: string;
  eventId: string;
  recurringEventId?: string;
  deleteMode?: RecurrenceUpdateMode;
}

export type DeleteEventParams = EventDeleteData & { provider: ProviderName };

export type EventResponse = CalendarEvent;

export type DeleteEventResponse = void;

export type EventListParams = {
  pageSize?: number;
  start?: Date | string;
  end?: Date | string;
  calendarIds?: {
    [key in ProviderName]?: string[];
  };
  nextPageToken?: string;
  /**
   * @deprecated Apple paging state is encoded in nextPageToken; this field is ignored.
   */
  appleToken?: Record<string, { lastIndex?: number }>;
  singleEvents?: boolean;
};

export interface EventsListResponse {
  events: CalendarEvent[];
  pageSize?: number;
  nextPageToken?: string;
}

/**
 * Common Authorization parameters
 */
export interface AuthorizeParams {
  /**
   * External user identifier from the client application
   */
  userId: string;

  /**
   * Optional scope parameter to request specific access levels ('read-write' | 'free-busy' | 'read')
   */
  scope?: string;

  /**
   * Optional state parameter to maintain across the flow
   */
  state?: string;

  /**
   * Optional providers parameter (comma-separated list: 'google,apple,microsoft,caldav')
   */
  providers?: string;

  /**
   * Optional language code for the Connect authorization pages, e.g. `'es'`.
   * For the languages Connect supports, see https://mobiscroll.com/docs/connect/localization#supported-languages
   *
   * Passed to the authorize URL as `lng`. When omitted, the Connect UI falls back to the
   * browser's Accept-Language header, then English.
   */
  lng?: string;

  /**
   * Optional color scheme of the Connect authorization pages: `'light'`, `'dark'` or `'auto'`
   * (follows the device setting). When omitted, the project's default theme applies.
   */
  theme?: 'light' | 'dark' | 'auto';
}

/**
 * OAuth2 token response
 */
export interface TokenResponse {
  /**
   * JWT bearer token for API authentication
   */
  access_token: string;

  /**
   * Token type (always "Bearer")
   */
  token_type: string;

  /**
   * Token lifetime in seconds
   */
  expires_in?: number;

  refresh_token?: string;
}

/**
 * Connected account information
 */
export interface ConnectedAccount {
  /**
   * Account identifier (usually email)
   */
  id: string;

  display?: string;

  /**
   * Scopes the provider actually granted for this account.
   *
   * Not necessarily the scopes Connect asked for: Google's consent screen lets the user
   * untick the calendar permission and still complete sign-in. Empty for Apple and
   * CalDav, which authenticate with a username and app password.
   */
  grantedScopes: string[];

  /**
   * Whether this account granted calendar access sufficient for your project's scope.
   *
   * `false` means the account is connected but no calendars can be read from it — the
   * user has to reconnect and allow calendar access. `null` means the question does not
   * apply (Apple, CalDav) or no scopes were recorded for the account.
   */
  calendarPermissionGranted: boolean | null;

  /**
   * Whether the stored credentials for this account still work.
   *
   * `reauth_required` means the provider has rejected them — the user revoked access, an
   * administrator withdrew consent, or the credentials were invalidated — so the account's
   * calendars have stopped syncing. Distinct from `calendarPermissionGranted`, which records
   * what was agreed at consent time and never changes afterwards. An account with no observed
   * failure is `active`.
   *
   * It cannot be repaired from your backend; send the user through the connect flow again.
   */
  syncState: 'active' | 'reauth_required';

  /** ISO 8601 timestamp of the last `syncState` change, or `null` if it has never changed. */
  syncStateUpdatedAt: string | null;
}

/**
 * Connection status response
 */
export interface ConnectionStatusResponse {
  /**
   * Connected accounts per provider
   */
  connections: {
    google: ConnectedAccount[];
    microsoft: ConnectedAccount[];
    apple: ConnectedAccount[];
    caldav: ConnectedAccount[];
  };

  /**
   * Whether the account limit has been reached
   */
  limitReached: boolean;
}

export type DisconnectParams = {
  provider: 'google' | 'microsoft' | 'apple' | 'caldav';
  /**
   * `id` of the account to disconnect, from `getConnectionStatus()` (`connections[provider][].id`).
   * Omit to disconnect every account of the provider.
   */
  account?: string;
};

export interface DisconnectResponse {
  success: boolean;
  message?: string;
}

export interface RevokeResponse {
  success: boolean;
}

/**
 * Parameters for subscribing to webhook notifications on a calendar
 */
export interface SubscribeWebhookParams {
  provider: ProviderName;
  calendarId: string;
  /** Auto-generated server-side when omitted. */
  channelId?: string;
  /** Provider-specific expiration timestamp (ms epoch). */
  expiration?: number;
}

export interface WebhookSubscription {
  channelId: string;
  /** Present for Google. */
  resourceId?: string;
  /** ISO 8601 timestamp; present for some providers. */
  expiration?: string;
}

export interface SubscribeWebhookResponse {
  success: boolean;
  provider: string;
  subscription: WebhookSubscription;
  serverWebhookUrl: string;
  channelId: string;
}

/**
 * Parameters for unsubscribing from webhook notifications on a calendar
 */
export interface UnsubscribeWebhookParams {
  provider: ProviderName;
  channelId: string;
  /** Required by some providers (e.g. Google) to fully unsubscribe. */
  resourceId?: string;
}

export interface UnsubscribeWebhookResponse {
  success: boolean;
  /**
   * Explanatory message, e.g. when the provider-side subscription had already expired.
   * `success` is still `true` in that case — treat the response as final regardless.
   */
  message?: string;
}

/**
 * Request headers of a webhook delivery: a plain header object (Node `IncomingHttpHeaders`,
 * any casing) or a Fetch API `Headers` instance.
 */
export type WebhookHeaders =
  | Record<string, string | string[] | undefined>
  | { get(name: string): string | null };

export interface VerifyWebhookSignatureOptions {
  /** Maximum age, in seconds, of `webhook-timestamp` in either direction. Default 300. */
  toleranceSeconds?: number;
  /** Current Unix time in seconds; defaults to the system clock. */
  now?: number;
}

/**
 * A verified webhook delivery sent by Mobiscroll Connect to the project's webhook URL.
 */
export interface WebhookDelivery {
  provider: ProviderName;
  userId: string;
  calendarId: string;
  events: CalendarEvent[];
  changeType?: 'created' | 'updated' | 'deleted' | 'mixed';
  /** ISO 8601 timestamp of when Connect processed the change. */
  timestamp: string;
  metadata: {
    channelId: string;
    eventCount: number;
    isInitialSync?: boolean;
  };
}

export type WebhookVerificationFailure =
  | 'missing_headers'
  | 'invalid_timestamp'
  | 'timestamp_out_of_tolerance'
  | 'no_public_keys'
  | 'no_matching_signature'
  | 'invalid_payload';

/**
 * Thrown when a webhook delivery fails verification. Do not process the delivery. Respond with
 * `503` when `reason` is `no_public_keys` (the keys could not be fetched, so Connect retries), and
 * with `401` otherwise (Connect does not retry).
 */
export class WebhookVerificationError extends MobiscrollConnectError {
  constructor(
    message: string,
    public reason: WebhookVerificationFailure
  ) {
    super(message, 'WEBHOOK_VERIFICATION_ERROR');
    this.name = 'WebhookVerificationError';
  }
}
