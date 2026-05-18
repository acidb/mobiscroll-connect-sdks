# CLAUDE.md — Mobiscroll Connect Go SDK

## AI Assistant Guidelines

Communication:

- Be terse. Skip preamble.
- Code first, brief explanation after.
- If you don't know a library version, say so.

Coding standards:

- Go 1.22 baseline. Idiomatic stdlib-first style; no Lombok-style code generation.
- Every exported symbol gets a godoc comment that starts with the identifier name.
- Errors are values; no panics across API boundaries.
- All resource methods take `context.Context` as the first parameter.
- Optional fields on parameter structs use pointer types (`*T`); use `mobiscroll.Ptr(v)` to fill them inline.
- Use `errors.As` to extract typed SDK errors; `errors.Is` works for the underlying transport error via `*NetworkError.Unwrap`.

## Project overview

- **Module:** `github.com/acidb/mobiscroll-connect-sdks/sdks/go`
- **Package:** `mobiscroll` (declared with `package mobiscroll`)
- **Entry point:** [`Client`](client.go) via `NewClient(...)`
- **HTTP:** stdlib `net/http` with a custom round-tripper for Bearer auth + 401 retry
- **JSON:** stdlib `encoding/json`
- **Min Go:** 1.22 (CI matrix runs 1.22, 1.23, 1.24)
- **Tests:** stdlib `testing` + `net/http/httptest`
- **Lint:** `golangci-lint`
- **Runtime deps:** only `golang.org/x/sync/singleflight`

## Architecture

```
Client                           public facade
  ├── Auth() -> *authService     GenerateAuthURL, GetToken, SetCredentials,
  │                              GetConnectionStatus, Disconnect
  ├── Calendars() -> *calendarsService   List
  └── Events() -> *eventsService          List, Create, Update, Delete

apiClient                        internal HTTP layer
  ├── execute()                  Bearer header, 401 -> refresh + retry-once
  ├── postForm()                 OAuth token-exchange / refresh (no retry loop)
  └── singleflight.Group         dedup of concurrent refreshes

Provider                         enum-style string with constants
config                           unexported; built by ClientOption funcs
errors.go                        6 typed errors + MobiscrollError interface
internal/querybuilder            URL query encoder (booleans -> "true"/"false")
```

### Token refresh

On 401, `apiClient.refreshTokens` calls `singleflight.Do("refresh", doRefresh)`. The first goroutine performs the `POST /oauth/token` with `grant_type=refresh_token`; concurrent goroutines wait for the same result. After a successful refresh, the original request is rebuilt and retried exactly once. If the retry still fails, the caller receives the mapped error. The token-exchange and refresh paths use a separate `*http.Client` (`tokenHTTP`) so they cannot recurse into the 401 retry loop.

Token merge: the existing `refresh_token` is preserved when the server omits one in the refresh response (`TokenResponse.mergedWith`).

### Error mapping

| HTTP | Type | Extra |
|---|---|---|
| 401 / 403 | `*AuthenticationError` | — |
| 404 | `*NotFoundError` | — |
| 400 / 422 | `*ValidationError` | `Details json.RawMessage` |
| 429 | `*RateLimitError` | `RetryAfter int` (from `Retry-After`) |
| 5xx | `*ServerError` | `StatusCode int` |
| Transport | `*NetworkError` | `Err error` (`Unwrap`) |

All satisfy `MobiscrollError`. The hierarchy maps one-to-one to the cross-SDK taxonomy in the root [`CLAUDE.md`](../../CLAUDE.md).

### JSON

- camelCase wire format via explicit struct tags (`json:"calendarId"` etc.).
- `omitempty` on every optional field.
- Pointer types (`*T`) for genuinely optional booleans and ints, so they can be omitted when nil.
- `mobiscroll.Ptr(v)` is a generic helper to take the address of a literal inline.

## Essential commands

```bash
go test -race -count=1 ./...                       # full reactor with race detector
go test -race -run TestRefreshDedup -v             # concurrency test in isolation
go build ./...                                     # confirm compile
golangci-lint run                                  # lint
gofmt -l .                                         # format check (no output = clean)
cd minimal-app && go run .                         # run the demo (needs env vars)
```

## File map

| File | Purpose |
|---|---|
| `client.go` | Public `Client` facade; constructor; resource accessors |
| `config.go` | `ClientOption`s; default base URL + timeout |
| `provider.go` | `Provider` string-enum + constants |
| `errors.go` | Typed errors + `MobiscrollError` interface + `mapResponseError` |
| `models.go` | All request/response DTOs; `Ptr[T]` helper |
| `transport.go` | `apiClient`: Bearer injection, 401 retry, singleflight refresh, request marshalling |
| `auth.go` | `authService`: OAuth flow + connection status + disconnect |
| `calendars.go` | `calendarsService.List` |
| `events.go` | `eventsService.List/Create/Update/Delete`; JSON-encoded `calendarIds` |
| `internal/querybuilder/querybuilder.go` | URL query builder (booleans -> strings, time.Time -> RFC3339) |
| `testsupport/testserver.go` | `httptest.Server` wrapper with FIFO response queue + request log |
| `*_test.go` | One test file per source file; concurrency test in `transport_test.go` |
| `minimal-app/main.go` | Reference demo (cookies + handlers + static pages) |
| `minimal-app/static/` | CSS + JS (copied from the Java demo) |

## Testing patterns

- Every test boots a fresh `testsupport.NewMockServer(t)` and queues responses via `Enqueue` / `EnqueueJSON` / `EnqueueStatus`. Never make real network calls.
- The mock server registers `t.Cleanup` for shutdown; no manual `Close` needed.
- Table-driven tests are preferred where the only thing varying is HTTP status + assertion (see `errors_test.go`).
- The concurrency test (`TestRefreshDedup`) fires N parallel calls against a server that returns 401 for each, then a single token response, then N successes; asserts exactly one `POST /oauth/token` was recorded. Always run the suite with `-race`.

## Key invariants

- `apiClient.tokenHTTP` is the only HTTP client that touches `/oauth/token`. It has no Bearer wrapping and cannot recurse into the 401 loop.
- `singleflight.Group.Do` key is the literal string `"refresh"` — there is at most one in-flight refresh per `apiClient`.
- `TokenResponse.mergedWith` preserves the existing refresh token when the server omits one.
- `Auth.GetConnectionStatus` and `Auth.Disconnect` try `/oauth/*` first and fall back to the legacy path (`/connection-status`, `/disconnect`) on 404 to support older server deployments.
- `GenerateAuthURL` builds the URL from `config.baseURL` — never a hardcoded host.
- Token-exchange and refresh send both `Authorization: Basic` AND a `CLIENT_ID` header.
- `Auth.Disconnect` uses `POST` (matches Node/Python/PHP/.NET/Java).
- `Events.List`'s `calendarIds` is a `map[Provider][]string` JSON-encoded into a single query parameter — matches the cross-SDK wire format.
- Version is sourced from `version.go` (`const Version = "X.Y.Z"`). `scripts/bump-version.sh go X.Y.Z` updates it.
- Release tag format is `sdks/go/vX.Y.Z` — this is mandated by the Go module proxy, which requires the module path as the tag prefix. The other SDKs use `<sdk>-v*`; Go is the documented exception.
