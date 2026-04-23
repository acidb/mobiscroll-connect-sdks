# CLAUDE.md — Mobiscroll Connect PHP SDK

## AI Assistant Guidelines

Communication:

- Be extremely concise. Skip introductory and concluding fluff (e.g., "Here is the code," "I'd be happy to help"). Get straight to the answer.
- If a question is about code, provide the code block first, then explain the logic briefly afterward.
- Never hallucinate. If you don't know something or a library version is too new, say so immediately.
- Focus on architectural patterns, edge cases, and optimization. Do not explain basic programming concepts.

Coding Standards:

- Default to PHP 8.1+ with strict types for all examples.
- Favor modular, reusable code. Include proper error handling and edge cases; do not just provide the "happy path."
- Keep logic decoupled from framework-specific concerns where possible.

Formatting:

- When modifying existing code, provide the entire function or class for easy copy-pasting. Never truncate code with comments like `// ... rest of code` unless the file is excessively long.
- Use PHPDoc for public methods to explain parameters and return types.

## Project Overview

PHP 8.1+ client library for the Mobiscroll Connect API. Enables calendar and event management across Google Calendar, Microsoft Outlook, Apple Calendar, and CalDAV through a single SDK.

- **Packagist**: `mobiscroll/connect-php`
- **Namespace**: `Mobiscroll\Connect`
- **HTTP client**: Guzzle 7
- **Test framework**: PHPUnit 10 + Mockery
- **Static analysis**: PHPStan (level 8)

---

## Architecture

```
MobiscrollConnectClient        — public entry point; constructs and wires all internals
  └── ApiClient                — HTTP layer (Guzzle); auth headers; token refresh; error mapping
        ├── Resources/Auth     — generateAuthUrl, getToken, setCredentials, getConnectionStatus, disconnect
        ├── Resources/Calendars — list()
        └── Resources/Events   — list(), create(), update(), delete()

Config                         — readonly DTO: clientId, clientSecret, redirectUri
TokenResponse                  — readonly DTO: access_token, token_type, expires_in, refresh_token
CalendarEvent                  — readonly DTO for API event responses
EventsListResponse             — readonly DTO wrapping []CalendarEvent + pagination
Calendar                       — readonly DTO for calendar list entries
ConnectionStatusResponse       — readonly DTO for /oauth/connection-status
DisconnectResponse             — readonly DTO for /oauth/disconnect

Exceptions/
  MobiscrollConnectException   — base; adds getCodeString()
  AuthenticationError          — 401/403
  ValidationError              — 400/422; adds getDetails()
  NotFoundError                — 404
  RateLimitError               — 429; adds getRetryAfter()
  ServerError                  — 5xx; adds getStatusCode()
  NetworkError                 — connection failures
```

### Token refresh

`ApiClient` catches `AuthenticationError` on every `get/post/put/delete` call. If a refresh token is stored, it silently POSTs to `oauth/token` with Basic auth (`base64(clientId:clientSecret)`), merges the new tokens (preserving the old refresh token if the server does not issue a new one), invokes the `onTokensRefreshed` callback, then retries the original request once. `postRaw` is excluded from retry — it is used for the token exchange endpoints themselves.

### Path normalisation

All request paths are `ltrim`-ed to strip leading slashes. Guzzle `base_uri` is `https://connect.mobiscroll.com/api/` (trailing slash required for relative resolution).

### Route fallbacks in Auth

`getConnectionStatus` and `disconnect` attempt `/oauth/*` first, then fall back to the legacy path without the `/oauth` prefix. This handles older server deployments.

---

## Essential Commands

```bash
composer install          # install dependencies
composer run test         # phpunit
composer run stan         # phpstan analyse src
composer run lint         # phpstan analyse src tests --level=8
vendor/bin/phpunit tests/Unit/AuthTest.php   # single test file
```

---

## File Map

| File                               | Purpose                                                                       |
| ---------------------------------- | ----------------------------------------------------------------------------- |
| `src/MobiscrollConnectClient.php`  | Public API surface; constructs ApiClient + resources                          |
| `src/ApiClient.php`                | HTTP methods, token refresh, error mapping                                    |
| `src/Config.php`                   | Immutable client config DTO                                                   |
| `src/TokenResponse.php`            | OAuth token DTO                                                               |
| `src/CalendarEvent.php`            | Event response DTO                                                            |
| `src/EventsListResponse.php`       | Paginated events response DTO                                                 |
| `src/Calendar.php`                 | Calendar response DTO                                                         |
| `src/ConnectionStatusResponse.php` | Connection status DTO                                                         |
| `src/DisconnectResponse.php`       | Disconnect result DTO                                                         |
| `src/Resources/Auth.php`           | OAuth flow methods                                                            |
| `src/Resources/Calendars.php`      | Calendar listing                                                              |
| `src/Resources/Events.php`         | Event CRUD + query building                                                   |
| `src/Exceptions/`                  | Exception hierarchy                                                           |
| `tests/Unit/`                      | PHPUnit tests (Auth, Calendars, Events, ConnectionStatusResponse, Exceptions) |
| `tests/Smoke/`                     | Minimal app smoke test                                                        |
| `minimal-app/public/index.php`     | Reference implementation backend                                              |
| `minimal-app/public/ui.php`        | Reference implementation frontend UI                                          |

---

## Coding Standards

- `declare(strict_types=1)` on every file.
- All properties on DTOs are `public readonly`.
- Use named arguments for constructor calls where it aids clarity.
- PHPDoc `@param` and `@return` annotations are required on public methods and where generics cannot be inferred (`array<string, mixed>`, `array<int, T>`, etc.).
- No inline comments except where logic is genuinely non-obvious. Do not add explanatory prose comments.
- JsDoc-style block comments (`/** ... */`) are used only for method signatures and type annotations — not for restating what the code does.
- Match expressions preferred over if/else chains for HTTP status mapping.
- Exception subclasses set their own defaults in the constructor — callers should not need to pass HTTP status codes.

---

## Testing Patterns

- Private methods are tested via `ReflectionObject` / `ReflectionMethod` — do not make them public just to test.
- Private properties (e.g. `onTokensRefreshed` callback) are accessed via reflection in tests that need to verify registration.
- Mockery is available for mocking Guzzle — use `Mockery::mock(GuzzleClient::class)` and inject via constructor replacement where needed.
- `BaseTestCase` in `tests/Unit/` bootstraps a real `MobiscrollConnectClient` with dummy credentials for tests that don't make HTTP calls.
- Do not mock the API responses with hardcoded strings unless the test is specifically about HTTP parsing. Prefer exercising `fromArray()` methods directly.

---

## Key Invariants

- `postRaw` never retries on 401 — it is the token exchange path itself.
- Token merge on refresh: `array_merge($existing, $new, $new->refresh_token !== null ? [] : ['refresh_token' => $oldRefreshToken])`.
- `buildListQuery` converts `pageSize` to string and caps it at 1000. `singleEvents` is serialised as `'true'`/`'false'` strings (not booleans) because it goes into a URL query string.
- `CalendarEvent::fromArray` throws `\InvalidArgumentException` for missing required fields (`provider`, `id`, `calendarId`).
- All HTTP verbs set `Authorization: Bearer {token}` headers — never embed tokens in query strings or request bodies.
