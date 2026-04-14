<?php

declare(strict_types=1);

use Mobiscroll\Connect\MobiscrollConnectClient;

session_start();

require __DIR__ . '/../vendor/autoload.php';

$envFile = __DIR__ . '/../.env';
if (file_exists($envFile)) {
    $lines = file($envFile, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES);
    foreach ($lines as $line) {
        if (str_starts_with($line, '#')) {
            continue;
        }
        [$key, $value] = explode('=', $line, 2) + [null, ''];
        if ($key !== null && $value !== null) {
            putenv(trim($key) . '=' . trim($value));
        }
    }
}

function envValue(string $key, string $default = ''): string
{
    $value = getenv($key);
    return $value === false ? $default : $value;
}

function jsonResponse(array $payload, int $status = 200): void
{
    http_response_code($status);
    header('Content-Type: application/json');
    echo json_encode($payload, JSON_PRETTY_PRINT);
}

/**
 * @return array<string, mixed>
 */
function requestJsonBody(): array
{
    $raw = file_get_contents('php://input');
    if (!is_string($raw) || trim($raw) === '') {
        return [];
    }

    $data = json_decode($raw, true);
    return is_array($data) ? $data : [];
}

/**
 * @return array<string, mixed>
 */
function calendarEventToArray(\Mobiscroll\Connect\CalendarEvent $event): array
{
    return [
        'provider' => $event->provider,
        'id' => $event->id,
        'calendarId' => $event->calendarId,
        'title' => $event->title,
        'start' => $event->start->format('c'),
        'end' => $event->end->format('c'),
        'allDay' => $event->allDay,
        'recurringEventId' => $event->recurringEventId,
        'color' => $event->color,
        'location' => $event->location,
        'attendees' => $event->attendees,
        'custom' => $event->custom,
        'conference' => $event->conference,
        'availability' => $event->availability,
        'privacy' => $event->privacy,
        'status' => $event->status,
        'link' => $event->link,
        'original' => $event->original,
    ];
}

$envClientId = envValue('MOBISCROLL_CLIENT_ID');
$envClientSecret = envValue('MOBISCROLL_CLIENT_SECRET');
$redirectUri = envValue('MOBISCROLL_REDIRECT_URI', 'http://localhost:8080/?action=callback');

if (isset($_GET['client_id']) && $_GET['client_id'] !== '') {
    $_SESSION['cfg_client_id'] = (string)$_GET['client_id'];
}
if (isset($_GET['client_secret']) && $_GET['client_secret'] !== '') {
    $_SESSION['cfg_client_secret'] = (string)$_GET['client_secret'];
}
if (isset($_GET['user_id']) && $_GET['user_id'] !== '') {
    $_SESSION['cfg_user_id'] = (string)$_GET['user_id'];
}
if (isset($_GET['scope']) && $_GET['scope'] !== '') {
    $_SESSION['cfg_scope'] = (string)$_GET['scope'];
}
if (isset($_GET['providers']) && $_GET['providers'] !== '') {
    $_SESSION['cfg_providers'] = (string)$_GET['providers'];
}

$clientId = $_SESSION['cfg_client_id'] ?? $envClientId;
$clientSecret = $_SESSION['cfg_client_secret'] ?? $envClientSecret;
$userId = $_SESSION['cfg_user_id'] ?? envValue('MOBISCROLL_USER_ID', 'test-user-123');
$scope = $_SESSION['cfg_scope'] ?? 'read-write';
$provider = $_SESSION['cfg_providers'] ?? envValue('MOBISCROLL_PROVIDER', 'google');

if ($clientId === '' || $clientSecret === '') {
    jsonResponse([
        'ok' => false,
        'error' => 'No client credentials configured. Open the Configuration panel in the UI and save your Client ID and Client Secret.',
        'hint' => 'Visit /?action=ui and expand the Configuration panel, or set MOBISCROLL_CLIENT_ID and MOBISCROLL_CLIENT_SECRET in your .env file.',
    ], 400);
    exit;
}

$client = new MobiscrollConnectClient(
    clientId: $clientId,
    clientSecret: $clientSecret,
    redirectUri: $redirectUri,
);

$client->onTokensRefreshed(function (\Mobiscroll\Connect\TokenResponse $updatedTokens): void {
    $_SESSION['access_token'] = $updatedTokens->access_token;
    $_SESSION['token_type'] = $updatedTokens->token_type;
    $_SESSION['expires_in'] = $updatedTokens->expires_in;
    $_SESSION['refresh_token'] = $updatedTokens->refresh_token;
});

$code = $_GET['code'] ?? null;
$action = $_GET['action'] ?? ($code !== null ? 'callback' : 'auth-url');

if ($action === 'config') {
    jsonResponse([
        'clientId' => $envClientId,
        'clientSecret' => $envClientSecret,
        'userId' => envValue('MOBISCROLL_USER_ID', ''),
        'scope' => envValue('MOBISCROLL_SCOPE', 'read-write'),
        'providers' => envValue('MOBISCROLL_PROVIDER', ''),
    ]);
    exit;
}

try {
    if ($action === 'callback' && $code !== null) {
        $token = $client->auth()->getToken($code);

        $_SESSION['access_token'] = $token->access_token;
        $_SESSION['token_type'] = $token->token_type;
        $_SESSION['expires_in'] = $token->expires_in;
        $_SESSION['refresh_token'] = $token->refresh_token;

        if (($_GET['response'] ?? '') === 'json') {
            jsonResponse([
                'ok' => true,
                'action' => 'callback',
                'message' => 'Token stored in session. Now you can call /calendars and /events.',
                'token' => [
                    'access_token' => substr($token->access_token, 0, 20) . '...',
                    'expires_in' => $token->expires_in,
                ],
            ]);
            exit;
        }

        header('Location: /?action=ui&callback=success', true, 302);
        exit;
    }

    if ($action === 'ui') {
        require __DIR__ . '/ui.php';
        exit;
    }

    if ($action === 'calendars-page') {
        require __DIR__ . '/calendars.php';
        exit;
    }

    if ($action === 'events-page') {
        require __DIR__ . '/events.php';
        exit;
    }

    if ($action === 'event-edit-page') {
        require __DIR__ . '/event-edit.php';
        exit;
    }

    if (in_array($action, ['calendars', 'events', 'connection-status', 'create-event', 'update-event', 'delete-event', 'disconnect'], true)) {
        if (empty($_SESSION['access_token'])) {
            jsonResponse([
                'ok' => false,
                'error' => 'No stored token. Complete OAuth flow first.',
                'next' => 'Visit /?action=auth-url to start OAuth',
            ], 401);
            exit;
        }

        if (isset($_SESSION['access_token'])) {
            $tokenData = new \Mobiscroll\Connect\TokenResponse(
                access_token: $_SESSION['access_token'],
                token_type: $_SESSION['token_type'] ?? 'Bearer',
                expires_in: $_SESSION['expires_in'] ?? null,
                refresh_token: $_SESSION['refresh_token'] ?? null,
            );
            $client->auth()->setCredentials($tokenData);
        }
    }

    switch ($action) {
        case 'auth-url':
            $authUrl = $client->auth()->generateAuthUrl(
                userId: $userId,
                scope: $scope,
                providers: $provider,
            );
            $callbackPreview = $redirectUri . (str_contains($redirectUri, '?') ? '&' : '?') . 'code=...';

            jsonResponse([
                'ok' => true,
                'action' => 'auth-url',
                'authUrl' => $authUrl,
                'redirectUri' => $redirectUri,
                'next' => 'Open authUrl in browser. After OAuth, you\'ll be redirected to ' . $callbackPreview,
            ]);
            break;

        case 'calendars':
            $calendars = $client->calendars()->list();
            jsonResponse($calendars);
            break;

        case 'connection-status':
            $connectionStatus = $client->auth()->getConnectionStatus();
            jsonResponse([
                'connections' => $connectionStatus->connections,
                'limitReached' => $connectionStatus->limitReached,
                'limit' => $connectionStatus->limit,
            ]);
            break;

        case 'events':
            $filters = [];

            if (!empty($_GET['start'])) {
                $filters['start'] = (string)$_GET['start'];
            }

            if (!empty($_GET['end'])) {
                $filters['end'] = (string)$_GET['end'];
            }

            if (isset($_GET['pageSize']) && $_GET['pageSize'] !== '') {
                $filters['pageSize'] = (int)$_GET['pageSize'];
            }

            if (!empty($_GET['nextPageToken'])) {
                $filters['nextPageToken'] = (string)$_GET['nextPageToken'];
            }

            if (isset($_GET['singleEvents']) && $_GET['singleEvents'] !== '') {
                $singleEvents = strtolower((string)$_GET['singleEvents']);
                $filters['singleEvents'] = in_array($singleEvents, ['1', 'true', 'yes', 'on'], true);
            }

            if (!empty($_GET['calendars'])) {
                if (is_array($_GET['calendars'])) {
                    $filters['calendars'] = array_values(array_filter(array_map('strval', $_GET['calendars']), static fn($id) => $id !== ''));
                } else {
                    $filters['calendars'] = array_values(array_filter(array_map('trim', explode(',', (string)$_GET['calendars']))));
                }
            }

            if (!empty($_GET['provider'])) {
                $filters['provider'] = (string)$_GET['provider'];
            } else {
                $filters['provider'] = $provider;
            }

            $events = $client->events()->list($filters ?: null);
            jsonResponse($events);
            break;

        case 'create-event':
            $body = requestJsonBody();
            if (!isset($body['provider']) || $body['provider'] === '') {
                $body['provider'] = $provider;
            }

            $createdEvent = $client->events()->create($body);
            jsonResponse(calendarEventToArray($createdEvent));
            break;

        case 'update-event':
            $body = requestJsonBody();
            if (!isset($body['provider']) || $body['provider'] === '') {
                $body['provider'] = $provider;
            }

            $updatedEvent = $client->events()->update($body);
            jsonResponse(calendarEventToArray($updatedEvent));
            break;

        case 'delete-event':
            $body = requestJsonBody();
            if (!isset($body['provider']) || $body['provider'] === '') {
                $body['provider'] = $provider;
            }

            $deleteResponse = $client->events()->delete($body);
            jsonResponse($deleteResponse !== [] ? $deleteResponse : ['success' => true]);
            break;

        case 'disconnect':
            $body = requestJsonBody();
            $disconnectProvider = $body['provider'] ?? $provider;
            $disconnectAccount = $body['account'] ?? null;

            $disconnectResponse = $client->auth()->disconnect(
                provider: $disconnectProvider,
                account: $disconnectAccount !== '' ? $disconnectAccount : null,
            );
            jsonResponse([
                'success' => $disconnectResponse->success,
                'message' => $disconnectResponse->message,
            ]);
            break;

        case 'session':
            jsonResponse([
                'ok' => true,
                'action' => 'session',
                'stored' => [
                    'has_access_token' => !empty($_SESSION['access_token']),
                    'token_type' => $_SESSION['token_type'] ?? null,
                    'expires_in' => $_SESSION['expires_in'] ?? null,
                ],
            ]);
            break;

        case 'clear-session':
            session_unset();
            session_regenerate_id(true);

            jsonResponse([
                'ok' => true,
                'action' => 'clear-session',
                'message' => 'Session token data cleared.',
            ]);
            break;

        default:
            jsonResponse([
                'ok' => false,
                'error' => 'Unknown action',
                'supportedActions' => ['config', 'ui', 'calendars-page', 'events-page', 'event-edit-page', 'auth-url', 'callback', 'calendars', 'events', 'connection-status', 'create-event', 'update-event', 'delete-event', 'disconnect', 'session', 'clear-session'],
            ], 400);
            break;
    }
} catch (Throwable $e) {
    if ($action === 'callback' && $e instanceof \Mobiscroll\Connect\Exceptions\NotFoundError) {
        jsonResponse([
            'ok' => false,
            'action' => $action,
            'error' => 'Authorization code is invalid, expired, already used, or was issued for a different redirect URI/client.',
            'class' => $e::class,
            'hint' => 'Start from /?action=auth-url (or /?action=ui), complete OAuth, and use the fresh callback URL returned by the provider without modifying the code parameter.',
        ], 400);
        exit;
    }

    jsonResponse([
        'ok' => false,
        'action' => $action,
        'error' => $e->getMessage(),
        'class' => $e::class,
    ], 500);
}
