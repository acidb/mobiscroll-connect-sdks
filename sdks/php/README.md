# Mobiscroll Connect PHP SDK

A PHP client library for the Mobiscroll Connect API, enabling seamless calendar and event management across multiple providers (Google Calendar, Microsoft Outlook, Apple Calendar, CalDAV).

📖 **[Full documentation](https://mobiscroll.com/docs/connect/php-sdk)**

## Features

- **Multi-provider support**: Google Calendar, Microsoft Outlook, Apple Calendar, CalDAV
- **OAuth2 authentication**: Full authorization code flow with token exchange
- **Automatic token refresh**: Silently refreshes expired access tokens and retries the original request
- **Event management**: Create, read, update, and delete calendar events
- **Calendar operations**: List calendars from all connected providers
- **Connection management**: Check provider connection status and disconnect accounts
- **Typed exceptions**: Distinct error classes for authentication, validation, rate limiting, and more
- **Type-safe**: PHP 8.1+ with strict typing throughout

## Requirements

- PHP 8.1 or higher
- Composer

## Installation

```bash
composer require mobiscroll/connect-php
```

## Setup

Create a Mobiscroll Connect application at the [Mobiscroll Connect dashboard](https://app.mobiscroll.com/connect) to obtain your **Client ID**, **Client Secret**, and configure your **Redirect URI**.

## Usage

### Initialize the Client

```php
use Mobiscroll\Connect\MobiscrollConnectClient;

$client = new MobiscrollConnectClient(
    clientId: 'YOUR_CLIENT_ID',
    clientSecret: 'YOUR_CLIENT_SECRET',
    redirectUri: 'YOUR_REDIRECT_URI',
);
```

### Token Refresh

The SDK automatically refreshes expired access tokens. When a request returns `401 Unauthorized` and a refresh token is available, the SDK silently exchanges it for a new access token and retries the request.

Register a callback to persist the updated tokens — this is required so the new tokens survive future requests:

```php
$client->onTokensRefreshed(function (\Mobiscroll\Connect\TokenResponse $updatedTokens): void {
    // Persist in your database or session store
    $_SESSION['access_token'] = $updatedTokens->access_token;
    $_SESSION['refresh_token'] = $updatedTokens->refresh_token;
    $_SESSION['expires_in'] = $updatedTokens->expires_in;
});
```

If the refresh token is invalid or revoked, the SDK throws `AuthenticationError` and the user must re-authorize.

### OAuth2 Flow

#### Step 1: Generate the authorization URL

```php
$authUrl = $client->auth()->generateAuthUrl(
    userId: 'your-app-user-id',
    // Optional:
    // scope: 'read-write',
    // state: 'csrf-protection-value',
    // providers: 'google,microsoft',
);

header('Location: ' . $authUrl);
```

#### Step 2: Handle the callback and exchange the code for tokens

```php
$code = $_GET['code'] ?? null;

$tokenResponse = $client->auth()->getToken($code);

// Persist all token fields — you need the refresh_token for auto-refresh
$_SESSION['access_token'] = $tokenResponse->access_token;
$_SESSION['token_type'] = $tokenResponse->token_type;
$_SESSION['expires_in'] = $tokenResponse->expires_in;
$_SESSION['refresh_token'] = $tokenResponse->refresh_token;
```

#### Step 3: Restore credentials and make API calls

```php
$client->auth()->setCredentials(new \Mobiscroll\Connect\TokenResponse(
    access_token: $_SESSION['access_token'],
    token_type: $_SESSION['token_type'] ?? 'Bearer',
    expires_in: $_SESSION['expires_in'] ?? null,
    refresh_token: $_SESSION['refresh_token'] ?? null,
));

// The client is now authenticated — make API calls
$calendars = $client->calendars()->list();
```

### Calendars

```php
$calendars = $client->calendars()->list();

foreach ($calendars as $calendar) {
    echo "{$calendar['provider']}: {$calendar['title']} ({$calendar['id']})\n";
}
```

### Events

#### List events

```php
$response = $client->events()->list([
    'start' => new DateTime('2024-01-01'),
    'end' => new DateTime('2024-01-31'),
    'calendarIds' => ['google' => ['primary']],
    'pageSize' => 50,
]);

foreach ($response['events'] as $event) {
    echo "{$event['title']}: {$event['start']} – {$event['end']}\n";
}

// Load the next page
if (!empty($response['nextPageToken'])) {
    $next = $client->events()->list([
        'pageSize' => 50,
        'nextPageToken' => $response['nextPageToken'],
    ]);
}
```

#### Create an event

```php
$event = $client->events()->create([
    'provider' => 'google',
    'calendarId' => 'primary',
    'title' => 'Team Meeting',
    'start' => '2024-06-15T10:00:00Z',
    'end' => '2024-06-15T11:00:00Z',
    'description' => 'Quarterly review',
    'location' => 'Conference Room A',
]);

echo "Created: {$event->id}\n";
```

#### Update an event

```php
$updated = $client->events()->update([
    'provider' => 'google',
    'calendarId' => 'primary',
    'eventId' => 'event-id-to-update',
    'title' => 'Team Meeting (Rescheduled)',
    'start' => '2024-06-15T14:00:00Z',
    'end' => '2024-06-15T15:00:00Z',
]);
```

#### Delete an event

```php
$client->events()->delete([
    'provider' => 'google',
    'calendarId' => 'primary',
    'eventId' => 'event-id-to-delete',
]);
```

#### Recurring events

```php
// Update only this instance of a recurring event
$client->events()->update([
    'provider' => 'google',
    'calendarId' => 'primary',
    'eventId' => 'instance-id',
    'recurringEventId' => 'series-id',
    'updateMode' => 'this',
    'title' => 'One-off title change',
]);

// Delete this and all following instances
$client->events()->delete([
    'provider' => 'google',
    'calendarId' => 'primary',
    'eventId' => 'instance-id',
    'recurringEventId' => 'series-id',
    'deleteMode' => 'following',
]);
```

### Connection Management

```php
// Check which providers are connected
$status = $client->auth()->getConnectionStatus();

foreach ($status->connections as $provider => $accounts) {
    echo "{$provider}: " . count($accounts) . " account(s)\n";
}

if ($status->limitReached) {
    echo "Connection limit of {$status->limit} reached\n";
}

// Disconnect a provider
$result = $client->auth()->disconnect(provider: 'google');

if ($result->success) {
    echo "Disconnected successfully\n";
}
```

## Error Handling

All SDK methods throw exceptions that extend `MobiscrollConnectException`:

| Exception | HTTP Status | Extra method |
|---|---|---|
| `AuthenticationError` | 401, 403 | — |
| `ValidationError` | 400, 422 | `getDetails(): array` |
| `NotFoundError` | 404 | — |
| `RateLimitError` | 429 | `getRetryAfter(): ?int` |
| `ServerError` | 5xx | `getStatusCode(): int` |
| `NetworkError` | — | — |

```php
use Mobiscroll\Connect\Exceptions\{
    AuthenticationError,
    ValidationError,
    NotFoundError,
    RateLimitError,
    ServerError,
    NetworkError,
    MobiscrollConnectException,
};

try {
    $events = $client->events()->list();
} catch (AuthenticationError $e) {
    // Token expired and refresh failed — re-authorize the user
} catch (ValidationError $e) {
    $details = $e->getDetails(); // field-level errors
} catch (NotFoundError $e) {
    // Calendar or event not found
} catch (RateLimitError $e) {
    $retryAfter = $e->getRetryAfter(); // seconds
} catch (ServerError $e) {
    $status = $e->getStatusCode(); // 500, 502, 503, 504
} catch (NetworkError $e) {
    // Connection failed
} catch (MobiscrollConnectException $e) {
    // Catch-all
}
```

## Testing

```bash
# Install dependencies
composer install

# Run all tests
composer run test

# Run a specific test file
vendor/bin/phpunit tests/Unit/AuthTest.php
```

## Project Structure

```
src/
├── Exceptions/
│   ├── MobiscrollConnectException.php
│   ├── AuthenticationError.php
│   ├── ValidationError.php
│   ├── NotFoundError.php
│   ├── RateLimitError.php
│   ├── ServerError.php
│   └── NetworkError.php
├── Resources/
│   ├── Auth.php
│   ├── Calendars.php
│   └── Events.php
├── ApiClient.php
├── Config.php
├── MobiscrollConnectClient.php
├── TokenResponse.php
├── Calendar.php
├── CalendarEvent.php
├── EventsListResponse.php
├── ConnectionStatusResponse.php
└── DisconnectResponse.php
tests/
├── Unit/
│   ├── AuthTest.php
│   ├── CalendarsTest.php
│   ├── ConnectionStatusResponseTest.php
│   ├── EventsTest.php
│   └── ExceptionsTest.php
└── Smoke/
    └── MinimalAppSmokeTest.php
```

## License

[MIT](LICENSE)
