# CLAUDE.md — Mobiscroll Connect Ruby SDK

## AI Assistant Guidelines

- Be terse. Code first, brief explanation after.
- Ruby 3.1 baseline. Idiomatic stdlib-first style.
- No magic — explicit `require`, no autoloading outside the gem entry point.
- Errors are values; surface them via typed subclasses of `Mobiscroll::Connect::Error`.

## Project overview

- **Gem name:** `mobiscroll-connect`
- **Module:** `Mobiscroll::Connect`
- **Entry point:** `Mobiscroll::Connect::Client.new(client_id:, client_secret:, redirect_uri:, **opts)`
- **HTTP:** Faraday ~> 2.9 (two separate connections; see token-refresh below)
- **Min Ruby:** 3.1 (CI matrix: 3.1, 3.2, 3.3)
- **Tests:** RSpec + WebMock
- **Lint:** RuboCop

## Architecture

```
Client                              public facade
  ├── auth     → Resources::Auth   generate_auth_url, get_token, set_credentials,
  │                                get_connection_status, disconnect
  ├── calendars → Resources::Calendars   list
  └── events   → Resources::Events      list, create, update, delete

ApiClient                           internal HTTP layer
  ├── execute()                     Bearer header, 401 → refresh + retry-once
  ├── post_form()                   OAuth token-exchange / refresh (no retry loop)
  └── Monitor + condition variable  dedup of concurrent refreshes

Provider                            string constants (GOOGLE, MICROSOFT, APPLE, CALDAV)
Config                              client_id, client_secret, redirect_uri, base_url, timeout
errors.rb                           6 typed errors + Connect.map_response_error
models.rb                           Value objects (Struct): TokenResponse, Calendar,
                                    CalendarEvent, RecurrenceRule, EventsListResponse, …
```

### Token refresh

On 401, `ApiClient#refresh_access_token!` acquires a `Monitor` lock and checks `@refresh_in_flight`. The first caller sets the flag and performs the refresh; concurrent callers `wait_while` on the condition variable and read `@refresh_result` after broadcast. After a successful refresh, the original request is retried exactly once. If the retry also fails, the caller receives the mapped error.

The token exchange / refresh path uses `@token_conn` (a separate Faraday connection) so it cannot recurse into the 401 retry loop.

Token merge: `TokenResponse#merged_with` preserves the existing `refresh_token` when the server omits one in the refresh response.

### Error mapping

| HTTP | Class | Extra |
|---|---|---|
| 401 / 403 | `AuthenticationError` | — |
| 404 | `NotFoundError` | — |
| 400 / 422 | `ValidationError` | `details` |
| 429 | `RateLimitError` | `retry_after` (seconds) |
| 5xx | `ServerError` | `status_code` |
| Transport | `NetworkError` | `cause` |

All are subclasses of `Mobiscroll::Connect::Error`.

### Wire format

- camelCase JSON on the wire. Model `from_h` methods translate `camelCase` hash keys → `snake_case` Ruby attributes.
- `calendar_ids` in `Events#list` is serialized as a JSON string in the `calendarIds` query parameter: `{"google":["primary"]}`.
- `Auth#get_connection_status` and `Auth#disconnect` try `/oauth/*` first, fall back to legacy paths on `NotFoundError`.
- Token exchange and refresh send both `Authorization: Basic <base64>` and `CLIENT_ID: <client_id>` headers.

## Essential commands

```bash
bundle install
bundle exec rspec                       # full test suite
bundle exec rspec spec/mobiscroll/connect/refresh_dedup_spec.rb  # concurrency test
bundle exec rubocop                     # lint
gem build mobiscroll-connect.gemspec    # confirm packaging

cd minimal-app && bundle install
cd minimal-app && bundle exec rackup -p 8080  # demo (needs .env)
```

## File map

| File | Purpose |
|---|---|
| `lib/mobiscroll-connect.rb` | RubyGems shim (requires `mobiscroll/connect`) |
| `lib/mobiscroll/connect.rb` | Top-level requires for all components |
| `lib/mobiscroll/connect/version.rb` | `VERSION = "X.Y.Z"` — single source of truth |
| `lib/mobiscroll/connect/client.rb` | Public `Client` facade; wires config + api_client + resources |
| `lib/mobiscroll/connect/config.rb` | `Config` with validated required params + defaults |
| `lib/mobiscroll/connect/provider.rb` | `Provider` string constants + `ALL` array |
| `lib/mobiscroll/connect/errors.rb` | Typed errors + `Connect.map_response_error` |
| `lib/mobiscroll/connect/models.rb` | Value structs with `from_h`/`to_h`/`to_wire` |
| `lib/mobiscroll/connect/api_client.rb` | Faraday HTTP layer; 401 retry; Monitor-based refresh dedup |
| `lib/mobiscroll/connect/resources/auth.rb` | OAuth flow + connection status + disconnect |
| `lib/mobiscroll/connect/resources/calendars.rb` | `Calendars#list` |
| `lib/mobiscroll/connect/resources/events.rb` | `Events#list/create/update/delete`; JSON `calendarIds` encoding |
| `spec/spec_helper.rb` | RSpec config + `WebMock.disable_net_connect!` |
| `spec/support/mock_server.rb` | WebMock helpers + default/credentialed client builders |
| `spec/mobiscroll/connect/*_spec.rb` | Per-module tests |
| `spec/mobiscroll/connect/refresh_dedup_spec.rb` | Concurrent 401 dedup test (threads) |
| `minimal-app/app.rb` | Sinatra demo app |
| `minimal-app/views/` | ERB templates |
| `minimal-app/public/` | Static assets (CSS + JS) |

## Testing patterns

- Every test stubs HTTP with WebMock — `WebMock.disable_net_connect!` in `spec_helper.rb`.
- `MockServer.stub_json`, `stub_form`, `stub_status` helpers in `spec/support/mock_server.rb`.
- `MockServer.default_client` / `client_with_tokens` build fresh clients for each example.
- The concurrency test (`refresh_dedup_spec.rb`) fires N threads, asserts the token stub was called at most once.
- Table-driven via `shared_examples` in `errors_spec.rb`.

## Key invariants

- `@token_conn` is the only Faraday connection that touches `/oauth/token`. It has no Bearer header and cannot trigger the 401 retry loop.
- `Monitor` + `@refresh_in_flight` flag ensures at most one in-flight refresh per `ApiClient`.
- `TokenResponse#merged_with` preserves the old `refresh_token` when the server omits one.
- `Auth#get_connection_status` and `Auth#disconnect` try `/oauth/*` first, fall back to legacy path on `NotFoundError`.
- `generate_auth_url` builds the URL from `config.base_url` — never a hardcoded host.
- Token exchange sends both `Authorization: Basic` and `CLIENT_ID` headers.
- `Events#list`'s `calendar_ids` is a `Hash` JSON-encoded into a single `calendarIds` query param.
- `CalendarEvent#end_time` maps the wire field `"end"` (avoids the Ruby keyword `end`).
- Version is sourced from `version.rb`. `scripts/bump-version.sh ruby X.Y.Z` updates it.
- Release tag format is `ruby-vX.Y.Z`.
