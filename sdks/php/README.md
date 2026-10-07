# Mobiscroll Connect PHP SDK

PHP client for [Mobiscroll Connect](https://mobiscroll.com/connect), the calendar connectivity layer for scheduling products — Google Calendar, Microsoft Outlook, Apple Calendar and CalDAV through one API. Backend only — works with your own UI.

[![Packagist](https://img.shields.io/packagist/v/mobiscroll/connect-php?label=Packagist)](https://packagist.org/packages/mobiscroll/connect-php)

**[Packagist](https://packagist.org/packages/mobiscroll/connect-php)** · **[Documentation](https://mobiscroll.com/docs/connect/php-sdk)** · **[Changelog](https://github.com/acidb/mobiscroll-connect-sdks/blob/main/sdks/php/CHANGELOG.md)** · **[Source](https://github.com/acidb/mobiscroll-connect-sdks/tree/main/sdks/php)**

## Features

- **Multi-provider support**: Google Calendar, Microsoft Outlook, Apple Calendar, CalDAV
- **OAuth2 authentication**: Full authorization code flow with token exchange
- **Automatic token refresh**: Silently refreshes expired access tokens and retries the original request
- **Event management**: Create, read, update, and delete calendar events
- **Calendar operations**: List calendars from all connected providers
- **Connection management**: Check provider connection status and disconnect accounts
- **Webhook verification**: Check the signature of webhook deliveries and parse them
- **Typed exceptions**: Distinct error classes for authentication, validation, rate limiting, and more
- **Type-safe**: PHP 8.1+ with strict typing throughout

## Requirements

- PHP 8.1 or higher, with the bundled `sodium` extension
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

**Running more than one instance.** The SDK does not deduplicate refreshes: under PHP-FPM every request is its own process, so concurrent requests for the same user each refresh on their own. Connect accepts concurrent refreshes of the same token, but refreshing with a copy that is two or more refreshes out of date revokes the user's authorization. Persist refreshed tokens to storage every request reads, such as your database, and call `$client->auth()->setCredentials(...)` with the current tokens at the start of each request. See [Refreshing from several instances](https://mobiscroll.com/docs/connect/api/oauth#concurrent-refresh).

### OAuth2 Flow

#### Step 1: Generate the authorization URL

```php
$authUrl = $client->auth()->generateAuthUrl(
    userId: 'your-app-user-id',
    // Optional:
    // scope: 'read-write',
    // state: 'csrf-protection-value',
    // providers: 'google,microsoft',
    // lng: 'es', // Connect page language, see https://mobiscroll.com/docs/connect/localization#supported-languages
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

### Webhooks

```php
// Subscribe to change notifications for a calendar
$subscription = $client->webhooks()->subscribeWebhook([
    'provider' => 'google',
    'calendarId' => 'primary',
]);

echo "Subscribed: {$subscription->channelId} -> {$subscription->serverWebhookUrl}\n";

// Unsubscribe when you no longer want notifications
$client->webhooks()->unsubscribeWebhook([
    'provider' => 'google',
    'channelId' => $subscription->channelId,
    'resourceId' => $subscription->subscription->resourceId,
]);
```

### Verify webhook deliveries

Every delivery to your webhook URL is signed. `verifyWebhook()` checks the signature and the timestamp, then returns the parsed `WebhookDelivery`, or throws `WebhookVerificationError`. Pass the **raw** request body, not a decoded array: re-encoding changes the bytes and every check fails.

```php
use Mobiscroll\Connect\Exceptions\WebhookVerificationError;

// webhook.php
try {
    $delivery = $client->webhooks()->verifyWebhook(file_get_contents('php://input'), getallheaders());
} catch (WebhookVerificationError $e) {
    // 503 lets Connect retry when the keys could not be loaded; 401 is final.
    http_response_code($e->getReason() === WebhookVerificationError::NO_PUBLIC_KEYS ? 503 : 401);
    exit;
}

http_response_code(204);
handleDelivery($delivery); // $delivery->userId, $delivery->calendarId, $delivery->events, ...
```

Headers can be any array with header names in any casing, or a PSR-7 request. In Laravel or Symfony, pass `$request->getContent()` and `$request->headers->all()`.

The public keys are fetched from `https://connect.mobiscroll.com/.well-known/webhook-keys` on the first delivery and cached for the whole process as the endpoint's `Cache-Control` allows. If no signature matches, the keys are fetched again (at most once a minute) before the delivery is rejected, so key rotations need no action on your side.

Under PHP-FPM every request is its own process, so without a shared cache each request fetches the keys on its first verification. Pass any PSR-16 cache (your framework's cache, APCu, Redis) as `webhookKeyCache` to share the keys and the fetch timestamps across requests and workers. Long-running workers (RoadRunner, Swoole, queue workers) keep the keys in memory either way.

```php
$client = new MobiscrollConnectClient(
    clientId: 'YOUR_CLIENT_ID',
    clientSecret: 'YOUR_CLIENT_SECRET',
    redirectUri: 'YOUR_REDIRECT_URI',
    webhookKeyCache: $cache, // any Psr\SimpleCache\CacheInterface
    webhookPublicKey: 'whpk_...', // optional
);
```

If your handler cannot make outbound requests, pin the key. `webhookPublicKey` is used only when the keys endpoint cannot be reached; it stops working when Mobiscroll retires that key, so you must replace it on every rotation.

To check against keys you supply, with no fetching, call `WebhookVerifier::verifyWebhookSignature($rawBody, $headers, ['whpk_...'])`. It throws `WebhookVerificationError` and returns nothing. See [Verifying deliveries](https://mobiscroll.com/docs/connect/api/webhooks#verifying-deliveries).

### Connection Management

```php
// Check which providers are connected
$status = $client->auth()->getConnectionStatus();

foreach ($status->connections as $provider => $accounts) {
    echo "{$provider}: " . count($accounts) . " account(s)\n";

    foreach ($accounts as $account) {
        // Google's consent screen lets the user untick the calendar permission and still
        // finish signing in. Such an account is connected but lists no calendars.
        if ($account['calendarPermissionGranted'] === false) {
            echo "  {$account['id']} must reconnect and allow calendar access\n";
        }
    }
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
| `WebhookVerificationError` | — | `getReason(): string` |

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
│   ├── NetworkError.php
│   └── WebhookVerificationError.php
├── Resources/
│   ├── Auth.php
│   ├── Calendars.php
│   ├── Events.php
│   └── Webhooks.php
├── ApiClient.php
├── Config.php
├── MobiscrollConnectClient.php
├── TokenResponse.php
├── Calendar.php
├── CalendarEvent.php
├── EventsListResponse.php
├── ConnectionStatusResponse.php
├── DisconnectResponse.php
├── SubscribeWebhookResponse.php
├── UnsubscribeWebhookResponse.php
├── WebhookSubscription.php
├── WebhookDelivery.php
├── WebhookDeliveryMetadata.php
├── WebhookEvent.php
├── WebhookVerifier.php
└── WebhookKeyStore.php
tests/
├── Unit/
│   ├── fixtures/webhook-vectors.json
│   ├── AuthTest.php
│   ├── CalendarsTest.php
│   ├── ConnectionStatusResponseTest.php
│   ├── EventsTest.php
│   ├── ExceptionsTest.php
│   ├── WebhooksTest.php
│   └── WebhookVerificationTest.php
└── Smoke/
    └── MinimalAppSmokeTest.php
```

## License

[MIT](LICENSE)
