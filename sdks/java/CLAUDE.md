# CLAUDE.md — Mobiscroll Connect Java SDK

## AI Assistant Guidelines

Communication:

- Be terse. Skip preamble.
- Code first, brief explanation after.
- If you don't know a library version, say so.

Coding standards:

- Java 11 baseline. No Lombok, no records (records are 14+).
- All classes are `final` unless inheritance is genuinely required (exception hierarchy is the exception).
- DTOs: private final fields, public getters, static `Builder` (or `@JsonCreator` constructor for response DTOs).
- Javadoc on every public type and method that isn't self-evident. No comments that restate code.
- No unchecked exceptions inside the library that aren't a subclass of `MobiscrollConnectException`.

## Project overview

- **Maven coordinate:** `com.mobiscroll:connect-sdk`
- **Package:** `com.mobiscroll.connect`
- **Entry point:** [`MobiscrollConnectClient`](connect-sdk/src/main/java/com/mobiscroll/connect/MobiscrollConnectClient.java)
- **HTTP client:** OkHttp 4 (30-second call timeout default)
- **JSON:** Jackson databind + `jackson-datatype-jsr310`
- **Min Java:** 11 (CI matrix runs 11, 17, 21)
- **Tests:** JUnit 5 + OkHttp MockWebServer + AssertJ
- **Build:** Maven (parent aggregator + `connect-sdk` library module + `minimal-app` Spring Boot demo)

## Architecture

```
MobiscrollConnectClient            facade; wires ApiClient + resources
  ├── Auth                          generateAuthUrl, getToken, setCredentials,
  │                                 getConnectionStatus, disconnect
  ├── Calendars                     list()
  └── Events                        list, create, update, delete

ApiClient                          OkHttp wrapper
  ├── execute()                     Bearer header, 401 → refresh → retry-once
  ├── postRaw()                     OAuth token exchange (no retry loop)
  └── AtomicReference<CompletableFuture<TokenResponse>> inflight
                                    CAS-based dedup of concurrent refreshes

Provider                            enum {GOOGLE, MICROSOFT, APPLE, CALDAV}
                                    wire form lowercase via @JsonValue/@JsonCreator
MobiscrollConnectConfig             immutable config + Builder
internal/JsonMapperHolder           singleton ObjectMapper
internal/QueryStringBuilder         booleans → "true"/"false"; OffsetDateTime → ISO-8601
exceptions/*                        7-class hierarchy under MobiscrollConnectException
```

### Token refresh

On 401, `ApiClient.refreshAccessToken()` does a `compareAndSet(null, future)` on `inflight`. The winner runs `postRaw("/oauth/token", grant_type=refresh_token)`, merges the new tokens into the existing credential (preserving the old `refresh_token` if the server omits one), fires `onTokensRefreshed`, and completes the future. Losers `inflight.get().join()` and share the result. The slot is always cleared in a `finally`. The original request is retried exactly once with the new bearer; if it still fails with 401, the caller gets `AuthenticationException`. `postRaw` itself never enters this retry loop.

### Error mapping

| HTTP | Exception | Extra |
|---|---|---|
| 401 / 403 | `AuthenticationException` | — |
| 404 | `NotFoundException` | — |
| 400 / 422 | `ValidationException` | `JsonNode details` |
| 429 | `RateLimitException` | `Integer retryAfter` from `Retry-After` |
| 5xx | `ServerException` | `int statusCode` |
| `IOException` / timeout | `NetworkException` | wraps cause |

All extend `MobiscrollConnectException(RuntimeException)`. The hierarchy maps one-to-one to the cross-SDK error taxonomy in the root [`CLAUDE.md`](../../CLAUDE.md).

### JSON

```java
new ObjectMapper()
    .registerModule(new JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .setSerializationInclusion(JsonInclude.Include.NON_NULL);
```

`Calendar` and `CalendarEvent` keep a `JsonNode original` plus a `@JsonAnyGetter/@JsonAnySetter` map for unmapped provider-specific fields — same role as the .NET `JsonElement Original`.

## Essential commands

```bash
mvn -B verify                                  # full reactor (library + minimal-app)
mvn -B -pl connect-sdk -am test                # library tests only (fast)
mvn -B -pl connect-sdk -am package             # build the publishable jar
mvn -B -pl connect-sdk -Dtest=TokenRefreshConcurrencyTest test
                                                # concurrency test in isolation
cd minimal-app && mvn spring-boot:run          # run the Spring Boot demo
```

## File map

| File | Purpose |
|---|---|
| `connect-sdk/src/main/java/com/mobiscroll/connect/MobiscrollConnectClient.java` | Public facade; constructs ApiClient + resources |
| `connect-sdk/src/main/java/com/mobiscroll/connect/ApiClient.java` | HTTP, Bearer auth, 401 retry, refresh dedup, error mapping |
| `connect-sdk/src/main/java/com/mobiscroll/connect/MobiscrollConnectConfig.java` | Immutable config + Builder |
| `connect-sdk/src/main/java/com/mobiscroll/connect/Provider.java` | Provider enum + JSON serialisation |
| `connect-sdk/src/main/java/com/mobiscroll/connect/resources/Auth.java` | OAuth flow |
| `connect-sdk/src/main/java/com/mobiscroll/connect/resources/Calendars.java` | List calendars |
| `connect-sdk/src/main/java/com/mobiscroll/connect/resources/Events.java` | Events CRUD |
| `connect-sdk/src/main/java/com/mobiscroll/connect/models/*.java` | Request/response DTOs |
| `connect-sdk/src/main/java/com/mobiscroll/connect/exceptions/*.java` | Exception hierarchy |
| `connect-sdk/src/main/java/com/mobiscroll/connect/internal/JsonMapperHolder.java` | ObjectMapper singleton |
| `connect-sdk/src/main/java/com/mobiscroll/connect/internal/QueryStringBuilder.java` | Query string encoding (booleans → strings) |
| `connect-sdk/src/test/java/com/mobiscroll/connect/ApiClientTest.java` | Refresh, retry, bearer injection |
| `connect-sdk/src/test/java/com/mobiscroll/connect/AuthTest.java` | OAuth flow + legacy-path fallback |
| `connect-sdk/src/test/java/com/mobiscroll/connect/CalendarsTest.java` | List parsing |
| `connect-sdk/src/test/java/com/mobiscroll/connect/EventsTest.java` | CRUD + query / JSON body |
| `connect-sdk/src/test/java/com/mobiscroll/connect/ErrorMappingTest.java` | HTTP status → exception |
| `connect-sdk/src/test/java/com/mobiscroll/connect/SerializationTest.java` | DTO JSON round-trips |
| `connect-sdk/src/test/java/com/mobiscroll/connect/TokenRefreshConcurrencyTest.java` | 10 parallel 401s → exactly one refresh |
| `connect-sdk/src/test/java/com/mobiscroll/connect/support/ClientFactory.java` | Helpers to build a client over MockWebServer |
| `minimal-app/src/main/java/com/mobiscroll/connect/sample/MinimalAppApplication.java` | Spring Boot entry |
| `minimal-app/src/main/java/com/mobiscroll/connect/sample/SdkConfig.java` | Wires one `MobiscrollConnectClient` bean from `application.yml` |
| `minimal-app/src/main/java/com/mobiscroll/connect/sample/ConnectController.java` | `/`, `/oauth/callback`, `/calendars`, `/events` |

## Testing patterns

- Every test boots a fresh `MockWebServer` in `@BeforeEach` and shuts it down in `@AfterEach`. Never make real HTTP calls.
- `ClientFactory.withMock(server)` / `withMockAndCredentials(server)` build a `MobiscrollConnectClient` pointed at the mock's `url("/api")`.
- For concurrent / timing-sensitive tests, use `MockWebServer.setDispatcher(...)` rather than `enqueue()` so the response depends on request inspection.
- AssertJ everywhere; `.satisfies(t -> { ... })` to drill into a thrown exception's typed fields.
- The token-refresh contract is verified twice: once for the happy path (`ApiClientTest.refreshesTokenOn401AndRetries`) and once under concurrency (`TokenRefreshConcurrencyTest.concurrent401sShareASingleRefresh`).

## Key invariants

- `inflight` is reset to `null` in the `finally` of the winning path — losers always see either an active future or `null` (in which case they CAS again).
- Token merge preserves the existing `refresh_token` if the server omits one.
- `postRaw` is the only path that bypasses the 401 retry loop. It is used by token exchange and refresh.
- `Auth.getConnectionStatus()` and `Auth.disconnect()` try `/oauth/*` first and fall back to the legacy path (e.g. `/connections`, `/disconnect`) on 404 to support older server deployments.
- `Auth.generateAuthUrl` derives the host from `ApiClient.getBaseUrl()` — never a hardcoded string.
- Token-exchange and refresh send both `Authorization: Basic` *and* a `CLIENT_ID` header (parity with .NET / Node / PHP / Python).
- `target/` and `.flattened-pom.xml` are build outputs — never edited directly.
- Version is sourced from `sdks/java/.mvn/maven.config` (`-Drevision=X.Y.Z`); both parent and child POMs consume `${revision}`. `flatten-maven-plugin` rewrites the published POM so consumers see the resolved version.
