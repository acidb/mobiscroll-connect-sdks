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
}

export type ProviderName = ProviderEnum.Google | ProviderEnum.Microsoft | ProviderEnum.Apple;

export const ProviderNames: ProviderName[] = [
  ProviderEnum.Google,
  ProviderEnum.Microsoft,
  ProviderEnum.Apple,
];

export type Calendar = {
  provider: ProviderName;
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

export interface CalendarEvent {
  provider: ProviderName;
  id: string;
  calendarId: string;
  title: string;
  start: Date;
  end: Date;
  allDay: boolean;
  recurringEventId?: string;
  color?: string;
  location?: string;
  attendees?: EventAttendee[];
  custom?: Record<string, unknown>;
  conference?: string;
  availability?: CalendarEventAvailability;
  privacy?: CalendarEventPrivacy;
  status?: CalendarEventStatus;
  link?: string;
  original: calendar_v3.Schema$Event | MicrosoftGraphEvent | VEvent;
}

export type WebhookEvent = CalendarEvent & {
  changeType: 'created' | 'updated' | 'deleted';
};

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
  };

  /**
   * Whether the account limit has been reached
   */
  limitReached: boolean;
}

export type DisconnectParams = {
  provider: 'google' | 'microsoft' | 'apple';
  account?: string;
};

export interface DisconnectResponse {
  success: boolean;
  message?: string;
}
