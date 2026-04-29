# Mobiscroll Connect Python SDK

Python client for the Mobiscroll Connect API — calendar and event management across Google Calendar, Microsoft Outlook, Apple Calendar, and CalDAV through a single SDK.

📖 **[Full documentation](https://mobiscroll.com/docs/connect/python-sdk)**

## Features

- **Multi-provider**: Google, Microsoft, Apple, CalDAV
- **OAuth2**: full authorization-code flow
- **Automatic token refresh** with persistence callback
- **Sync and async** clients (`MobiscrollConnectClient` / `mobiscroll_connect.aio.AsyncMobiscrollConnectClient`)
- **Typed responses** via frozen dataclasses
- **Typed exception hierarchy** for HTTP errors
- **Pagination** helpers (`iter_all` traverses every page)
- **Type-checked** (`py.typed` shipped)

## Installation

```bash
pip install mobiscroll-connect
```

Requires Python 3.9+.

## Quick start

```python
from mobiscroll_connect import MobiscrollConnectClient

with MobiscrollConnectClient(
    client_id="YOUR_CLIENT_ID",
    client_secret="YOUR_CLIENT_SECRET",
    redirect_uri="https://yourapp.example/oauth/callback",
) as client:
    # 1. Build the auth URL and redirect the user
    auth_url = client.auth.generate_auth_url(user_id="user-123")

    # 2. After callback: exchange the code for tokens
    tokens = client.auth.get_token(code="...")

    # 3. Use the API
    for calendar in client.calendars.list():
        print(calendar.provider, calendar.title)
```

## OAuth2 flow

```python
# Step 1 — generate auth URL (server-side)
auth_url = client.auth.generate_auth_url(
    user_id="user-123",
    scope="calendar",       # optional
    state="csrf-value",     # optional
    providers="google,microsoft",  # optional
)

# Step 2 — exchange the code (in your callback handler)
tokens = client.auth.get_token(code=request.query_params["code"])

# Persist tokens.access_token, tokens.refresh_token, tokens.expires_in

# Step 3 — restore credentials on subsequent requests
from mobiscroll_connect import TokenResponse

client.auth.set_credentials(TokenResponse(
    access_token=session["access_token"],
    refresh_token=session["refresh_token"],
    expires_in=session["expires_in"],
))
```

## Automatic token refresh

When a request returns `401 Unauthorized` and a refresh token is present, the SDK transparently refreshes and retries. Register a callback to persist the new tokens:

```python
def persist_tokens(tokens):
    db.update_tokens(user_id, tokens.to_dict())

client.on_tokens_refreshed(persist_tokens)
```

If the refresh itself fails (revoked, expired), `AuthenticationError` is raised — re-authorize the user.

## Calendars

```python
calendars = client.calendars.list()
for cal in calendars:
    print(f"{cal.provider}: {cal.title} ({cal.id})")
```

## Events

### List events

```python
from datetime import datetime

response = client.events.list(
    start=datetime(2024, 1, 1),
    end=datetime(2024, 1, 31),
    calendar_ids={"google": ["primary"]},
    page_size=50,
)

for event in response:           # EventsListResponse is iterable
    print(event.title, event.start, event.end)

if response.has_more:
    next_page = client.events.list(
        next_page_token=response.next_page_token,
        page_size=50,
    )
```

### Iterate all pages

```python
for event in client.events.iter_all(
    start=datetime(2024, 1, 1),
    end=datetime(2024, 12, 31),
    page_size=250,
):
    process(event)
```

### Create

```python
event = client.events.create({
    "provider": "google",
    "calendar_id": "primary",
    "title": "Team Meeting",
    "start": "2024-06-15T10:00:00Z",
    "end": "2024-06-15T11:00:00Z",
    "description": "Quarterly review",
    "location": "Conference Room A",
})
print("Created:", event.id)
```

### Update

```python
client.events.update({
    "provider": "google",
    "calendar_id": "primary",
    "event_id": "evt-123",
    "title": "Team Meeting (Rescheduled)",
    "start": "2024-06-15T14:00:00Z",
    "end": "2024-06-15T15:00:00Z",
})
```

### Delete

```python
client.events.delete({
    "provider": "google",
    "calendar_id": "primary",
    "event_id": "evt-123",
})
```

### Recurring events

```python
# Update only this instance
client.events.update({
    "provider": "google",
    "calendar_id": "primary",
    "event_id": "instance-id",
    "recurring_event_id": "series-id",
    "update_mode": "this",
    "title": "One-off change",
})

# Delete this and all following instances
client.events.delete({
    "provider": "google",
    "calendar_id": "primary",
    "event_id": "instance-id",
    "recurring_event_id": "series-id",
    "delete_mode": "following",
})
```

## Connection management

```python
status = client.auth.get_connection_status()
for provider, accounts in status.connections.items():
    print(f"{provider}: {len(accounts)} account(s)")

if status.limit_reached:
    print(f"Connection limit of {status.limit} reached")

# Disconnect a single account
client.auth.disconnect("google", account="user@gmail.com")

# Or all accounts of a provider
client.auth.disconnect("microsoft")
```

## Async usage

```python
import asyncio
from mobiscroll_connect.aio import AsyncMobiscrollConnectClient

async def main():
    async with AsyncMobiscrollConnectClient(
        client_id="...",
        client_secret="...",
        redirect_uri="...",
    ) as client:
        await client.auth.get_token(code="...")
        async for event in client.events.iter_all(start="2024-01-01", end="2024-01-31"):
            print(event.title)

asyncio.run(main())
```

## Error handling

| Exception             | HTTP status   | Extra          |
| --------------------- | ------------- | -------------- |
| `AuthenticationError` | 401, 403      | —              |
| `ValidationError`     | 400, 422      | `.details`     |
| `NotFoundError`       | 404           | —              |
| `RateLimitError`      | 429           | `.retry_after` |
| `ServerError`         | 5xx           | `.status_code` |
| `NetworkError`        | — (transport) | —              |

All errors inherit from `MobiscrollConnectError`.

```python
from mobiscroll_connect import (
    AuthenticationError, ValidationError, NotFoundError,
    RateLimitError, ServerError, NetworkError, MobiscrollConnectError,
)

try:
    client.events.list()
except AuthenticationError:
    # Refresh failed — re-authorize the user
    ...
except ValidationError as e:
    print(e.details)
except RateLimitError as e:
    print(f"Retry after {e.retry_after}s")
except ServerError as e:
    print(f"Server returned {e.status_code}")
except NetworkError:
    # Connection / DNS / timeout
    ...
except MobiscrollConnectError:
    # Catch-all
    ...
```

## Architecture

```
mobiscroll_connect/
├── __init__.py                — public re-exports
├── client.py                  — MobiscrollConnectClient (sync entry point)
├── api_client.py              — sync HTTP layer + token refresh
├── async_api_client.py        — async HTTP layer + token refresh
├── config.py                  — frozen Config dataclass
├── exceptions.py              — exception hierarchy
├── models.py                  — frozen dataclass response models
├── _internal/
│   ├── errors.py              — HTTP → exception mapper (shared)
│   └── payloads.py            — query/payload builders (shared)
├── resources/
│   ├── auth.py                — Auth (sync)
│   ├── calendars.py           — Calendars (sync)
│   └── events.py              — Events (sync)
└── aio/
    ├── client.py              — AsyncMobiscrollConnectClient
    └── resources.py           — AsyncAuth / AsyncCalendars / AsyncEvents
```

### Why these choices

- **Frozen dataclasses, not Pydantic.** No third-party runtime dependency for models — matches the "stdlib-only DTOs" approach of the PHP and Node SDKs and keeps install size small. Validation is done where it matters (response parsing, query builders).
- **`httpx` for both sync and async.** Single dependency, identical request API. `requests` would force a separate sync transport.
- **`asyncio.Lock` and `threading.Lock` for refresh dedup.** Concurrent 401s wait on the same in-flight refresh instead of racing — same invariant as the Node SDK's `refreshTokenPromise`.
- **Resources as attributes (`client.auth`, not `client.auth()`).** Idiomatic Python; the parens-method style in the PHP SDK exists only because PHP can't expose readonly properties cleanly.
- **Pagination helper (`iter_all`).** PHP/Node make callers manage `next_page_token` by hand; Python iterators are the natural shape and remove the bookkeeping.

## Testing

```bash
pip install -e ".[dev]"
pytest
```

## License

MIT
