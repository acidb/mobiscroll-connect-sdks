# CLAUDE.md — Mobiscroll Connect .NET SDK

## AI Assistant Guidelines

Communication:

- Be extremely concise. Skip introductory and concluding fluff (e.g., "Here is the code," "I'd be happy to help"). Get straight to the answer.
- If a question is about code, provide the code block first, then explain the logic briefly afterward.
- Never hallucinate. If you don't know something or a library version is too new, say so immediately.
- Focus on architectural patterns, edge cases, and optimization. Do not explain basic programming concepts.

Coding Standards:

- Default to C# with modern async/await patterns and nullable reference types enabled.
- Favor modular, reusable code. All public types are sealed unless inheritance is explicitly needed.
- Include proper error handling and edge cases; do not just provide the "happy path."
- Keep logic decoupled from ASP.NET Core specifics where possible — DI wiring belongs in `DependencyInjection/`, not in core SDK classes.

Formatting:

- When modifying existing code, provide the entire method or class for easy copy-pasting. Never truncate code with comments like `// ... rest of code` unless the file is excessively long.
- XML doc comments (`/// <summary>`) on all public members. Do not add comments that restate what the code does.

## Project Overview

.NET 8 client library for the Mobiscroll Connect API. Enables calendar and event management across Google Calendar, Microsoft Outlook, Apple Calendar, and CalDAV through a single SDK.

- **NuGet package**: `Mobiscroll.Connect`
- **Namespace**: `Mobiscroll.Connect`
- **Entry point**: `src/Mobiscroll.Connect/MobiscrollConnectClient.cs`
- **HTTP client**: `System.Net.Http.HttpClient` (30-second timeout)
- **Target framework**: .NET 8.0
- **Test framework**: xUnit 2.x + Moq + coverlet

---

## Architecture

```
MobiscrollConnectClient        — public entry point; constructs ApiClient + resources; holds credentials
  ├── Auth: Auth               — GenerateAuthUrl, GetTokenAsync, GetConnectionStatusAsync, DisconnectAsync
  ├── Calendars: Calendars     — ListAsync()
  └── Events: Events           — ListAsync(), CreateAsync(), UpdateAsync(), DeleteAsync()

ApiClient                      — HttpClient wrapper; Bearer auth; 401 → token refresh → retry; error mapping
  └── SemaphoreSlim            — deduplicates concurrent 401-triggered refreshes via _inflightRefresh Task

MobiscrollConnectConfig        — sealed DTO: ClientId, ClientSecret, RedirectUri
Provider (enum)                — Google, Microsoft, Apple, CalDav; serialised via ProviderJsonConverter
QueryStringBuilder (internal)  — builds URL query strings; booleans → "true"/"false" strings
ServiceCollectionExtensions    — AddMobiscrollConnect() for ASP.NET Core DI
Models/                        — request/response DTOs (sealed records/classes)
Exceptions/                    — typed exception hierarchy rooted at MobiscrollConnectException
```

### Token refresh

`ApiClient` intercepts 401 responses. If a refresh token is stored and the request has not already been retried, `RefreshAccessTokenAsync()` acquires a `SemaphoreSlim` lock. Concurrent 401s share a single in-flight `_inflightRefresh` Task — parallel failures await the same promise rather than each triggering a separate POST. After refresh, `OnTokensRefreshed` is invoked so the caller can persist the new credentials. The original request is then retried once with the updated Bearer token. `postRaw` (used for token exchange itself) is excluded from the retry path.

### Error mapping

HTTP responses are mapped in `ApiClient` to typed exceptions:

| HTTP status | Exception class        | Extra member           |
| ----------- | ---------------------- | ---------------------- |
| 401 / 403   | `AuthenticationException` | —                   |
| 404         | `NotFoundException`    | —                      |
| 400 / 422   | `ValidationException`  | `Details` (object?)    |
| 429         | `RateLimitException`   | `RetryAfter` (int?)    |
| 5xx         | `ServerException`      | `StatusCode` (int)     |
| Timeout / connection | `NetworkException` | —               |

All exceptions extend `MobiscrollConnectException`.

### JSON serialisation

```csharp
static readonly JsonSerializerOptions JsonOptions = new()
{
    PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
    PropertyNameCaseInsensitive = true,
    DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
    Converters = { new ProviderJsonConverter() },
};
```

`ProviderJsonConverter` maps `Provider.Google ↔ "google"`, `Provider.Microsoft ↔ "microsoft"`, etc. `CalendarEvent` and `Calendar` include an `Original` (`JsonElement`) field for unmapped provider-specific data.

---

## Essential Commands

```bash
dotnet build                                   # build solution
dotnet test                                    # run all tests
dotnet test --collect:"XPlat Code Coverage"    # with coverage
dotnet pack src/Mobiscroll.Connect             # produce .nupkg + .snupkg
dotnet add package Mobiscroll.Connect          # consume (in another project)
```

---

## File Map

| File | Purpose |
| ---- | ------- |
| `src/Mobiscroll.Connect/MobiscrollConnectClient.cs` | Public facade; constructs and wires all internals; `SetCredentials`, `OnTokensRefreshed` |
| `src/Mobiscroll.Connect/ApiClient.cs` | HTTP methods, Bearer auth, 401 retry, token refresh, error mapping |
| `src/Mobiscroll.Connect/MobiscrollConnectConfig.cs` | Immutable config DTO |
| `src/Mobiscroll.Connect/Provider.cs` | Provider enum + `ProviderJsonConverter` |
| `src/Mobiscroll.Connect/Resources/Auth.cs` | OAuth flow: GenerateAuthUrl, GetTokenAsync, GetConnectionStatusAsync, DisconnectAsync |
| `src/Mobiscroll.Connect/Resources/Calendars.cs` | ListAsync() |
| `src/Mobiscroll.Connect/Resources/Events.cs` | ListAsync, CreateAsync, UpdateAsync, DeleteAsync; ISO 8601 date formatting |
| `src/Mobiscroll.Connect/Models/` | Request/response DTOs (TokenResponse, CalendarEvent, EventCreateData, etc.) |
| `src/Mobiscroll.Connect/Exceptions/` | Exception hierarchy |
| `src/Mobiscroll.Connect/Internal/QueryStringBuilder.cs` | URL query string encoding; parity with Node/PHP wire format |
| `src/Mobiscroll.Connect/DependencyInjection/ServiceCollectionExtensions.cs` | `AddMobiscrollConnect()` for ASP.NET Core |
| `tests/Mobiscroll.Connect.Tests/ApiClientTests.cs` | Token refresh, 401 handling, constructor validation |
| `tests/Mobiscroll.Connect.Tests/AuthTests.cs` | OAuth flow tests |
| `tests/Mobiscroll.Connect.Tests/CalendarsTests.cs` | Calendar API tests |
| `tests/Mobiscroll.Connect.Tests/EventsTests.cs` | Event CRUD tests |
| `tests/Mobiscroll.Connect.Tests/ErrorMappingTests.cs` | Exception type mapping |
| `tests/Mobiscroll.Connect.Tests/SerializationTests.cs` | JSON round-trip tests |
| `tests/Mobiscroll.Connect.Tests/TestHelpers/FakeHttpMessageHandler.cs` | Queued-response HTTP handler; records requests for assertion |
| `tests/Mobiscroll.Connect.Tests/TestHelpers/ClientFactory.cs` | Builds test clients with FakeHttpMessageHandler and dummy config |
| `samples/MinimalApp/Program.cs` | Reference ASP.NET Core minimal API: full OAuth flow + CRUD demo |

---

## Coding Standards

- All public types are `sealed` unless inheritance is explicitly required.
- All public properties on DTOs/models are `public` and use init-only setters (`{ get; init; }`) or `required` where a value is mandatory.
- All async public methods accept a `CancellationToken cancellationToken = default` parameter and pass it through to `HttpClient` calls.
- `.ConfigureAwait(false)` on every `await` inside library code.
- `SemaphoreSlim` (not `lock`) for async-safe concurrency control.
- `JsonIgnoreCondition.WhenWritingNull` — never serialize `null` optional fields.
- Booleans in query strings are serialised as `"true"` / `"false"` strings via `QueryStringBuilder` — not `True`/`False` — to match the Node and PHP SDK wire format.
- XML doc comments (`/// <summary>`) on all public members. Do not comment internal or private methods unless the logic is genuinely non-obvious.
- No inline comments that restate what the code does.

---

## Testing Patterns

- `FakeHttpMessageHandler` queues pre-scripted `HttpResponseMessage` objects and records every outgoing request (method, URI, headers, body) for assertion.
- `ClientFactory` builds a `MobiscrollConnectClient` wired to a `FakeHttpMessageHandler` for every test — never make real HTTP calls in tests.
- Token refresh behaviour is tested in `ApiClientTests`: script a 401 followed by a valid token response, verify the retry fires and `OnTokensRefreshed` is invoked.
- `SerializationTests` verify JSON round-trips for models — use `System.Text.Json` directly, not the SDK's `JsonOptions`, so tests are independent of implementation internals.
- Do not use `Moq` for `HttpClient` — use `FakeHttpMessageHandler` instead; it gives full request inspection without the verbosity of mock setup.

---

## Key Invariants

- `_inflightRefresh` is set to `null` in a `finally` block — concurrent refresh calls share one in-flight Task, and the slot is always cleared.
- Token merge on refresh: new `AccessToken` replaces the old one; `RefreshToken` is preserved if the server does not issue a new one.
- `postRaw` never triggers the 401 retry — it is the token exchange path itself.
- `GetConnectionStatusAsync` and `DisconnectAsync` try `/oauth/*` first, then fall back to the legacy path without the `/oauth` prefix for older server deployments.
- `GenerateAuthUrl` builds the URL from `ApiClient.BaseAddress` — never a hardcoded string — so changing the base URL propagates automatically.
- `CLIENT_ID` header is sent alongside `Authorization: Basic` on token exchange requests (`GetTokenAsync` and `RefreshAccessTokenAsync`).
- `dist/` / build output is never edited directly; `src/` is the source of truth.
