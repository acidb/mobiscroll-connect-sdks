# Mobiscroll Connect PHP SDK

A PHP client library for the Mobiscroll Connect API, enabling seamless calendar and event management across multiple providers (Google Calendar, Microsoft Outlook, Apple iCal, etc.).

## Features

- **Multi-provider support**: Google Calendar, Microsoft Outlook, Apple iCal, and more
- **OAuth2 authentication**: Secure authorization flow with token refresh
- **Bearer token support**: Server-side authentication for backend integration
- **Event management**: Create, read, update, and delete calendar events
- **Calendar operations**: List and manage connected calendars
- **Type-safe**: Built with PHP 8.1+ with strict typing
- **Tested**: Comprehensive PHPUnit test suite included

## Installation

### Prerequisites

- PHP 8.1 or higher
- Composer

### Setup

1. Install PHP (if not already installed):
```bash
# macOS with Homebrew
brew install php

# Or use a Docker container
docker run -it php:8.1-cli
```

2. Install Composer:
```bash
# macOS with Homebrew
brew install composer

# Or download from https://getcomposer.org
```

3. Install the SDK:
```bash
composer require mobiscroll/connect-php
```

## Configuration

Create a Mobiscroll Connect application at https://connect.mobiscroll.com/admin to get your credentials:
- Client ID
- Client Secret
- Redirect URI

## Usage

### Basic Setup

```php
use Mobiscroll\Connect\MobiscrollConnectClient;

$client = new MobiscrollConnectClient(
    clientId: 'your-client-id',
    clientSecret: 'your-client-secret',
    redirectUri: 'http://localhost:3000/callback'
);
```

### OAuth2 Authorization Flow

#### Step 1: Generate Authorization URL

```php
$authUrl = $client->auth()->generateAuthUrl(
    scope: 'calendar',
    state: 'random-state-value'
);

// Redirect user to this URL
header("Location: $authUrl");
```

#### Step 2: Handle Authorization Callback

```php
// In your callback route handler
$code = $_GET['code'] ?? null;

if (!$code) {
    throw new Exception('Authorization code missing');
}

try {
    $tokenResponse = $client->auth()->getToken($code);
    
    // Store the token securely (e.g., in session, database, or secure cookie)
    $_SESSION['mobiscroll_token'] = $tokenResponse->access_token;
    $_SESSION['mobiscroll_refresh'] = $tokenResponse->refresh_token;
    $_SESSION['mobiscroll_expires'] = time() + ($tokenResponse->expires_in ?? 3600);
    
} catch (\Mobiscroll\Connect\Exceptions\AuthenticationError $e) {
    throw new Exception('Failed to get token: ' . $e->getMessage());
}
```

#### Step 3: Use the Token for API Calls

```php
// Restore token from storage
$tokenResponse = new \Mobiscroll\Connect\TokenResponse(
    access_token: $_SESSION['mobiscroll_token'],
    token_type: 'Bearer',
    expires_in: $_SESSION['mobiscroll_expires'] - time(),
    refresh_token: $_SESSION['mobiscroll_refresh']
);

$client->auth()->setCredentials($tokenResponse);

// Now you can make API calls
try {
    $calendars = $client->calendars()->list();
    
    foreach ($calendars as $calendar) {
        echo "Calendar: {$calendar->title} ({$calendar->id})\n";
    }
    
} catch (\Mobiscroll\Connect\Exceptions\MobiscrollConnectException $e) {
    echo "API Error: " . $e->getMessage() . "\n";
}
```

### Working with Calendars

```php
// List all calendars
$calendars = $client->calendars()->list();

foreach ($calendars as $calendar) {
    echo "ID: {$calendar->id}\n";
    echo "Title: {$calendar->title}\n";
    echo "Provider: {$calendar->provider}\n";
    echo "Timezone: {$calendar->timeZone}\n";
    echo "Color: {$calendar->color}\n";
}
```

### Working with Events

#### List Events

```php
$start = new DateTime('2024-01-01 00:00:00', new DateTimeZone('UTC'));
$end = new DateTime('2024-01-31 23:59:59', new DateTimeZone('UTC'));

$response = $client->events()->list([
    'start' => $start,
    'end' => $end,
    'calendars' => ['calendar-id-1', 'calendar-id-2'],
    'pageSize' => 50,
]);

foreach ($response->events as $event) {
    echo "Event: {$event->title}\n";
    echo "Start: " . $event->start->format('Y-m-d H:i:s') . "\n";
    echo "End: " . $event->end->format('Y-m-d H:i:s') . "\n";
    echo "Location: {$event->location}\n";
}

// Handle pagination
if ($response->nextPageToken) {
    $nextResponse = $client->events()->list([
        'pageSize' => 50,
        'nextPageToken' => $response->nextPageToken,
    ]);
}
```

#### Create Event

```php
$calendarId = 'calendar-id-to-add-to';

$event = $client->events()->create($calendarId, [
    'title' => 'Team Meeting',
    'start' => '2024-01-15T10:00:00',
    'end' => '2024-01-15T11:00:00',
    'location' => 'Conference Room A',
    'description' => 'Weekly team sync',
]);

echo "Created event: {$event->id}\n";
```

#### Update Event

```php
$updatedEvent = $client->events()->update($calendarId, $eventId, [
    'title' => 'Team Meeting (Rescheduled)',
    'start' => '2024-01-15T14:00:00',
    'end' => '2024-01-15T15:00:00',
]);

echo "Updated event: {$updatedEvent->title}\n";
```

#### Delete Event

```php
$client->events()->delete($calendarId, $eventId);
echo "Event deleted\n";
```

### Connection Management

```php
// Check connection status
try {
    $status = $client->auth()->getConnectionStatus();
    
    echo "Connections: \n";
    foreach ($status->connections as $provider => $calendars) {
        echo "  {$provider}: " . count($calendars) . " calendars\n";
    }
    
    if ($status->limitReached) {
        echo "Calendar limit reached: {$status->limit}\n";
    }
    
} catch (\Mobiscroll\Connect\Exceptions\MobiscrollConnectException $e) {
    echo "Failed to get status: " . $e->getMessage() . "\n";
}

// Disconnect a provider
try {
    $result = $client->auth()->disconnect('google');
    
    if ($result->success) {
        echo "Disconnected successfully\n";
    }
    
} catch (\Mobiscroll\Connect\Exceptions\MobiscrollConnectException $e) {
    echo "Failed to disconnect: " . $e->getMessage() . "\n";
}
```

## Error Handling

The SDK provides typed exceptions for different error scenarios:

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
    $client->calendars()->list();
    
} catch (AuthenticationError $e) {
    // Handle 401/403 authentication failures
    echo "Authentication failed: " . $e->getMessage() . "\n";
    
} catch (ValidationError $e) {
    // Handle 400/422 validation errors
    echo "Validation error: " . $e->getMessage() . "\n";
    if ($e->details) {
        echo "Details: " . json_encode($e->details) . "\n";
    }
    
} catch (NotFoundError $e) {
    // Handle 404 not found
    echo "Resource not found: " . $e->getMessage() . "\n";
    
} catch (RateLimitError $e) {
    // Handle 429 rate limit
    echo "Rate limited. Retry after: " . $e->retryAfter . " seconds\n";
    
} catch (ServerError $e) {
    // Handle 5xx server errors
    echo "Server error: " . $e->getMessage() . "\n";
    echo "Status code: " . $e->statusCode . "\n";
    
} catch (NetworkError $e) {
    // Handle network connectivity issues
    echo "Network error: " . $e->getMessage() . "\n";
    
} catch (MobiscrollConnectException $e) {
    // Handle other SDK errors
    echo "SDK error: " . $e->getMessage() . "\n";
}
```

## Testing

Run the test suite:

```bash
# Install dependencies
composer install

# Run all tests
composer run test

# Run tests with coverage
composer run test-coverage

# Run specific test file
vendor/bin/phpunit tests/Unit/AuthTest.php

# Run with watch (auto-rerun on file changes)
composer run test:watch
```

## Development

### Project Structure

```
mobiscroll-connect-php/
├── src/
│   ├── Exceptions/
│   │   ├── MobiscrollConnectException.php
│   │   ├── AuthenticationError.php
│   │   ├── ValidationError.php
│   │   ├── NotFoundError.php
│   │   ├── RateLimitError.php
│   │   ├── ServerError.php
│   │   └── NetworkError.php
│   ├── Resources/
│   │   ├── Auth.php
│   │   ├── Calendars.php
│   │   └── Events.php
│   ├── ApiClient.php
│   ├── Config.php
│   ├── MobiscrollConnectClient.php
│   ├── TokenResponse.php
│   ├── Calendar.php
│   ├── CalendarEvent.php
│   ├── EventsListResponse.php
│   ├── ConnectionStatusResponse.php
│   └── DisconnectResponse.php
├── tests/
│   └── Unit/
│       ├── AuthTest.php
│       ├── CalendarsTest.php
│       └── EventsTest.php
├── composer.json
├── phpunit.xml
└── README.md
```

### Running PHPStan Code Analysis

```bash
composer run stan      # Level 0 analysis
composer run lint      # Level 8 strict analysis
```

## API Reference

### MobiscrollConnectClient

Main client class for interacting with Mobiscroll Connect APIs.

**Constructor:**
```php
new MobiscrollConnectClient(
    string $clientId,
    string $clientSecret,
    string $redirectUri
)
```

**Methods:**
- `auth(): Auth` - Access authentication and connection management
- `calendars(): Calendars` - Access calendar operations
- `events(): Events` - Access event operations

### Auth Resource

**Methods:**
- `generateAuthUrl(string $scope = 'calendar', ?string $state = null): string`
- `getToken(string $code): TokenResponse`
- `setCredentials(TokenResponse $tokens): void`
- `getConnectionStatus(): ConnectionStatusResponse`
- `disconnect(string $provider): DisconnectResponse`

### Calendars Resource

**Methods:**
- `list(): Calendar[]` - Get all connected calendars

### Events Resource

**Methods:**
- `list(?array $params = null): EventsListResponse`
- `create(string $calendarId, array $event): CalendarEvent`
- `update(string $calendarId, string $eventId, array $event): CalendarEvent`
- `delete(string $calendarId, string $eventId): void`

## Comparison with Node.js SDK

This PHP SDK mirrors the architecture and interface of the Node.js SDK:

| Feature | Node.js SDK | PHP SDK |
|---------|------------|---------|
| HTTP Client | Axios | Guzzle HTTP |
| Test Framework | Jest | PHPUnit |
| Package Manager | npm | Composer |
| Token Refresh | Auto-refresh on 401 | Built-in error handling |
| Type Safety | TypeScript | PHP 8.1+ strict types |
| OAuth2 Support | ✅ | ✅ |
| Bearer Auth | ✅ | ✅ |
| Event Management | ✅ | ✅ |
| Calendar Management | ✅ | ✅ |

## License

ISC

## Support

For issues, questions, or feature requests, please contact support@mobiscroll.com or visit https://connect.mobiscroll.com/docs
