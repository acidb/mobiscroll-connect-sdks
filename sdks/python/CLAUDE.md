# CLAUDE.md — Mobiscroll Connect Python SDK

## AI Assistant Guidelines

Communication:

- Be extremely concise. Skip introductory and concluding fluff. Get straight to the answer.
- If a question is about code, provide the code block first, then explain briefly afterward.
- Never hallucinate. If you don't know something or a library version is too new, say so immediately.
- Focus on architectural patterns, edge cases, and optimization. Do not explain basic programming concepts.

Coding Standards:

- Default to Python 3.9+ compatible code with type annotations throughout.
- Favor modular, reusable code. Include proper error handling and edge cases; do not just provide the "happy path."
- Keep logic decoupled from transport concerns where possible.

Formatting:

- When modifying existing code, provide the entire function or class for easy copy-pasting. Never truncate code with comments like `# ... rest of code` unless the file is excessively long.
- Type annotations are required on all public methods and module-level functions.

## Project Overview

Python 3.9+ client library for the Mobiscroll Connect API. Enables calendar and event management across Google Calendar, Microsoft Outlook, Apple Calendar, and CalDAV through a single SDK.

- **PyPI**: `mobiscroll-connect`
- **Package**: `mobiscroll_connect`
- **HTTP client**: httpx (sync + async, same API surface)
- **Test framework**: pytest + pytest-asyncio + respx
- **Static analysis**: mypy (strict), ruff

---

## Architecture

```
MobiscrollConnectClient        — public sync entry point; constructs and wires all internals
  └── ApiClient                — sync HTTP layer (httpx); auth headers; token refresh; error mapping
        ├── resources/Auth     — generate_auth_url, get_token, set_credentials, get_connection_status, disconnect
        ├── resources/Calendars — list()
        └── resources/Events   — list(), iter_all(), create(), update(), delete()

mobiscroll_connect.aio:
AsyncMobiscrollConnectClient   — async counterpart; identical public API, all methods are coroutines
  └── AsyncApiClient           — async HTTP layer (httpx.AsyncClient); asyncio.Lock for refresh dedup
        ├── aio/resources/AsyncAuth, AsyncCalendars, AsyncEvents

Config                         — frozen dataclass: client_id, client_secret, redirect_uri, base_url, timeout
TokenResponse                  — frozen dataclass: access_token, token_type, expires_in, refresh_token
CalendarEvent                  — frozen dataclass for API event responses
EventsListResponse             — frozen dataclass wrapping list[CalendarEvent] + pagination; iterable
Calendar                       — frozen dataclass for calendar list entries
ConnectionStatusResponse       — frozen dataclass for /oauth/connection-status
DisconnectResponse             — frozen dataclass for /oauth/disconnect

Exceptions:
  MobiscrollConnectError       — base; .message and .code
  AuthenticationError          — 401/403
  ValidationError              — 400/422; .details
  NotFoundError                — 404
  RateLimitError               — 429; .retry_after
  ServerError                  — 5xx; .status_code
  NetworkError                 — transport failures

_internal/errors.py            — map_response_error, map_transport_error (pure functions, shared by sync+async)
_internal/payloads.py          — build_list_events_query, build_event_payload, build_delete_query (pure, shared)
```

### Token refresh

`ApiClient` catches a 401 on every `get/post/put/delete` call. If a refresh token is stored, it acquires a `threading.Lock` (sync) or `asyncio.Lock` (async), POSTs to `oauth/token` with Basic auth (`base64(client_id:client_secret)`), merges the new tokens (preserving the old refresh token if the server does not issue a new one), invokes the `on_tokens_refreshed` callback, then retries the original request once. `post_form` is excluded from retry — it is the token exchange path itself.

### Path normalisation

All request paths are `lstrip`-ed to strip leading slashes. httpx `base_url` is `https://connect.mobiscroll.com/api` with paths resolved relatively.

### Route fallbacks in Auth

`get_connection_status` and `disconnect` attempt `/oauth/*` first, catch `MobiscrollConnectError`, then fall back to the legacy path without the `/oauth` prefix. This matches PHP SDK behavior and handles older server deployments.

---

## Essential Commands

```bash
# Install in editable mode with dev dependencies
pip install -e ".[dev]"

# Run tests
pytest

# Single test file
pytest tests/test_auth.py -v

# Type checking
mypy mobiscroll_connect

# Linting
ruff check mobiscroll_connect tests

# Run the minimal Flask test app
cd minimal-app
pip install -r requirements.txt
python app.py
# Open http://localhost:8000/?action=ui
```

---

## File Map

| File                                       | Purpose                                                            |
| ------------------------------------------ | ------------------------------------------------------------------ |
| `mobiscroll_connect/client.py`             | Public sync API surface; constructs ApiClient + resources          |
| `mobiscroll_connect/api_client.py`         | Sync HTTP methods, token refresh, error mapping                    |
| `mobiscroll_connect/async_api_client.py`   | Async HTTP layer; identical surface, asyncio.Lock                  |
| `mobiscroll_connect/aio/client.py`         | AsyncMobiscrollConnectClient                                       |
| `mobiscroll_connect/aio/resources.py`      | AsyncAuth, AsyncCalendars, AsyncEvents                             |
| `mobiscroll_connect/config.py`             | Immutable client config dataclass                                  |
| `mobiscroll_connect/models.py`             | All response DTOs (frozen dataclasses with `from_dict` classmethods)|
| `mobiscroll_connect/exceptions.py`         | Exception hierarchy                                                |
| `mobiscroll_connect/resources/auth.py`     | OAuth flow methods                                                 |
| `mobiscroll_connect/resources/calendars.py`| Calendar listing                                                   |
| `mobiscroll_connect/resources/events.py`   | Event CRUD + iter_all pagination helper                            |
| `mobiscroll_connect/_internal/errors.py`   | HTTP status → exception mapping (shared)                           |
| `mobiscroll_connect/_internal/payloads.py` | Query/payload builders (shared, pure functions)                    |
| `tests/`                                   | pytest tests (auth, calendars, events, models, payloads, async)    |
| `minimal-app/app.py`                       | Reference Flask implementation (action-dispatch pattern)           |
| `minimal-app/templates/`                   | Dark-theme UI pages (ui, calendars, events, event_edit)            |

---

## Coding Standards

- All models are `@dataclass(frozen=True)` — no Pydantic, zero extra runtime dependencies.
- Wire format is camelCase; all Python-facing properties are snake_case. `from_dict` classmethods handle the mapping.
- Resources are attributes on the client (`client.events.list()`), not callable getters.
- Use `from __future__ import annotations` on every file for forward-reference compatibility with Python 3.9.
- No inline comments except where logic is genuinely non-obvious.

---

## Testing Patterns

- HTTP is mocked with `respx` — each test function is decorated with `@respx.mock` (do not use it as a class decorator; it silently skips test methods in pytest).
- Async tests use `pytest-asyncio` with `asyncio_mode = "auto"` in `pyproject.toml` — no `@pytest.mark.asyncio` needed.
- `tests/conftest.py` provides a `make_client()` fixture with dummy credentials.
- Do not mock model parsing with hardcoded strings; prefer calling `from_dict` directly in unit tests.

---

## Key Invariants

- `post_form` never retries on 401 — it is the token exchange path itself.
- Token merge on refresh: `refresh_token = new_tokens.refresh_token or current.refresh_token`.
- `build_list_events_query` caps `page_size` at 1000 and serialises `single_events` as `"true"`/`"false"` strings (not booleans) — it goes into a URL query string.
- `CalendarEvent.from_dict` raises `ValueError` for missing required fields (`provider`, `id`, `calendarId`).
- All HTTP verbs set `Authorization: Bearer {token}` headers — never embed tokens in query strings or bodies.
- `on_tokens_refreshed` callback exceptions are swallowed — persistence failures must not break the refresh path.
- The async `on_tokens_refreshed` callback can be sync or async; `AsyncApiClient` detects coroutines via `asyncio.iscoroutine` and awaits them.
