/**
 * Configuration options for the Mobiscroll Connect client
 */
export interface MobiscrollConnectConfig {
  /**
   * API key for authentication
   */
  apiKey: string;

  /**
   * Base URL for the API
   * @default 'https://connect.mobiscroll.com/api'
   */
  baseURL?: string;

  /**
   * Request timeout in milliseconds
   * @default 30000
   */
  timeout?: number;

  /**
   * Custom headers to include in requests
   */
  headers?: Record<string, string>;
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

/**
 * Calendar resource from calendar providers
 */
export interface Calendar {
  id: string;
  title: string;
  description?: string;
  provider: 'google' | 'microsoft' | 'apple';
  timeZone?: string;
  color?: string;
  original?: unknown;
  accessRole?: string;
  selected?: boolean;
  createdAt?: string;
  updatedAt?: string;
  [key: string]: unknown;
}

/**
 * Event resource from calendar providers
 */
export interface Event {
  id: string;
  calendarId: string;
  title: string;
  description?: string;
  start?: Date;
  end?: Date;
  allDay?: boolean;
  location?: string;
  color?: string;
  provider?: 'google' | 'microsoft' | 'apple';
  createdAt?: string;
  updatedAt?: string;
  [key: string]: unknown;
}

export interface ProviderPagingToken {
  nextPageToken?: string;
  nextLink?: string;
  lastIndex?: number;
}

export interface ProviderPagingState {
  token?: Record<string, ProviderPagingToken>;
  isDepleted?: boolean;
}

export interface EventsPagingState {
  google?: ProviderPagingState;
  microsoft?: ProviderPagingState;
  apple?: ProviderPagingState;
}

export interface ListParams {
  limit?: number;
  offset?: number;
  sort?: string;
  order?: 'asc' | 'desc';
}

/**
 * Event filters for querying
 */
export interface EventFilters {
  start?: string | null;
  end?: string | null;
  calendarIds?: {
    google: string[];
    microsoft: string[];
    apple: string[];
  };
}

/**
 * Event list parameters
 */
export interface EventListParams {
  /**
   * Number of events to fetch per request (max 1000)
   * @default 250
   */
  pageSize?: number;

  /**
   * Filters for querying events
   */
  filters?: EventFilters;

  /**
   * Base64 encoded pagination state from previous response
   */
  paging?: string;

  /**
   * Controls how recurring events are handled
   * - true: Expands recurring events into individual instances
   * - false: Returns only the master recurring event
   * @default true
   */
  singleEvents?: boolean;

  /**
   * Access token for authentication (overrides API key)
   */
  accessToken?: string;
}

/**
 * Events list response
 */
export interface EventsListResponse {
  events: Event[];
  pageSize: number;
  paging?: string;
  info?: Record<string, string>;
}

/**
 * Create calendar data
 */
export interface CreateCalendarData {
  title: string;
  description?: string;
  timeZone?: string;
  color?: string;
}

/**
 * Update calendar data
 */
export interface UpdateCalendarData {
  title?: string;
  description?: string;
  timeZone?: string;
  color?: string;
}

/**
 * Recurrence rule for recurring events
 */
export interface EventRecurrence {
  /**
   * Frequency of recurrence (DAILY, WEEKLY, MONTHLY, YEARLY)
   */
  frequency: 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY';

  /**
   * Interval between occurrences (e.g., every 2 weeks)
   * @default 1
   */
  interval?: number;

  count?: number;

  /**
   * End date in YYYYMMDDTHHMMSSZ format
   */
  until?: string;

  /**
   * Days of the week (MO, TU, WE, TH, FR, SA, SU)
   */
  byDay?: string[];
}

/**
 * Create event data
 */
export interface CreateEventData {
  calendarId: string;
  title: string;
  description?: string;

  /**
   * Start date/time in ISO format
   */
  start: string;

  /**
   * End date/time in ISO format
   */
  end: string;

  /**
   * Whether this is an all-day event
   * @default false
   */
  allDay?: boolean;

  location?: string;

  /**
   * Recurrence rule for recurring events
   */
  recurrence?: EventRecurrence;

  /**
   * List of attendee email addresses
   */
  attendees?: string[];

  /**
   * Custom provider-specific properties
   */
  custom?: Record<string, unknown>;

  availability?: 'busy' | 'free';
  privacy?: 'public' | 'private' | 'confidential';
  status?: 'confirmed' | 'tentative' | 'cancelled';
}

/**
 * Update modes for recurring events
 */
export type UpdateMode = 'this' | 'following' | 'all';

/**
 * Delete modes for recurring events
 */
export type DeleteMode = 'this' | 'following' | 'all';

/**
 * Update event data
 */
export interface UpdateEventData {
  calendarId: string;
  eventId: string;

  /**
   * Recurring event ID (if editing a recurring event instance)
   */
  recurringEventId?: string;

  /**
   * Update mode for recurring events (this, following, all)
   */
  updateMode?: UpdateMode;

  title?: string;
  description?: string;

  /**
   * Start date/time in ISO format
   */
  start?: string;

  /**
   * End date/time in ISO format
   */
  end?: string;

  allDay?: boolean;
  location?: string;

  /**
   * Recurrence rule for recurring events
   */
  recurrence?: EventRecurrence;

  /**
   * List of attendee email addresses
   */
  attendees?: string[];

  /**
   * Custom provider-specific properties
   */
  custom?: Record<string, unknown>;

  availability?: 'busy' | 'free';
  privacy?: 'public' | 'private' | 'confidential';
  status?: 'confirmed' | 'tentative' | 'cancelled';
}

/**
 * Delete event parameters
 */
export interface DeleteEventParams {
  provider: 'google' | 'microsoft' | 'apple';
  calendarId: string;
  eventId: string;

  /**
   * Recurring event ID (if deleting a recurring event instance)
   */
  recurringEventId?: string;

  /**
   * Delete mode for recurring events (this, following, all)
   */
  deleteMode?: DeleteMode;
}

/**
 * Event response (created or updated event)
 * Successful responses spread the CalendarEvent properties at root level
 * along with the original provider response.
 */
export interface EventResponse {
  success: boolean;

  /**
   * Error message if success is false
   */
  message?: string;

  provider?: 'google' | 'microsoft' | 'apple';
  id?: string;
  calendarId?: string;
  title?: string;
  description?: string;
  start?: Date;
  end?: Date;
  allDay?: boolean;
  location?: string;
  color?: string;

  /**
   * Original provider-specific event data
   */
  original?: unknown;

  [key: string]: unknown;
}

/**
 * Delete event response
 */
export interface DeleteEventResponse {
  success: boolean;
  message?: string;
}

// ============================================================================
// Auth Types
// ============================================================================

/**
 * Parameters for initiating OAuth2 authorization
 */
export interface AuthorizeParams {
  /**
   * Client application identifier
   */
  clientId: string;

  /**
   * External user identifier from the client application
   */
  userId: string;

  userName?: string;
  userEmail?: string;

  /**
   * Callback URL after authorization completes
   */
  redirectUri?: string;

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
   * Whether the account connection limit has been reached
   */
  limitReached?: boolean;

  /**
   * Maximum number of accounts allowed
   */
  limit?: number;
}

/**
 * Parameters for disconnecting a provider account
 */
export interface DisconnectParams {
  provider: 'google' | 'microsoft' | 'apple';

  /**
   * Optional account ID to disconnect.
   * If omitted, disconnects all accounts for the provider
   */
  account?: string;
}

/**
 * Disconnect response
 */
export interface DisconnectResponse {
  success: boolean;
}

// ============================================================================
// Error Classes
// ============================================================================

/**
 * Base error class for all SDK errors
 */
export class MobiscrollConnectError extends Error {
  constructor(
    message: string,
    public code?: string
  ) {
    super(message);
    this.name = 'MobiscrollConnectError';
    Object.setPrototypeOf(this, MobiscrollConnectError.prototype);
  }
}

/**
 * Error thrown when authentication fails
 */
export class AuthenticationError extends MobiscrollConnectError {
  constructor(message: string = 'Authentication failed') {
    super(message, 'AUTH_ERROR');
    this.name = 'AuthenticationError';
    Object.setPrototypeOf(this, AuthenticationError.prototype);
  }
}

/**
 * Error thrown when a resource is not found
 */
export class NotFoundError extends MobiscrollConnectError {
  constructor(message: string = 'Resource not found') {
    super(message, 'NOT_FOUND');
    this.name = 'NotFoundError';
    Object.setPrototypeOf(this, NotFoundError.prototype);
  }
}

/**
 * Error thrown when a request is invalid
 */
export class ValidationError extends MobiscrollConnectError {
  constructor(
    message: string = 'Validation failed',
    public details?: unknown
  ) {
    super(message, 'VALIDATION_ERROR');
    this.name = 'ValidationError';
    Object.setPrototypeOf(this, ValidationError.prototype);
  }
}

/**
 * Error thrown when rate limit is exceeded
 */
export class RateLimitError extends MobiscrollConnectError {
  constructor(
    message: string = 'Rate limit exceeded',
    public retryAfter?: number
  ) {
    super(message, 'RATE_LIMIT');
    this.name = 'RateLimitError';
    Object.setPrototypeOf(this, RateLimitError.prototype);
  }
}

/**
 * Error thrown when the server returns an error
 */
export class ServerError extends MobiscrollConnectError {
  constructor(
    message: string = 'Server error',
    public statusCode?: number
  ) {
    super(message, 'SERVER_ERROR');
    this.name = 'ServerError';
    Object.setPrototypeOf(this, ServerError.prototype);
  }
}

/**
 * Error thrown when a network request fails
 */
export class NetworkError extends MobiscrollConnectError {
  constructor(message: string = 'Network request failed') {
    super(message, 'NETWORK_ERROR');
    this.name = 'NetworkError';
    Object.setPrototypeOf(this, NetworkError.prototype);
  }
}
