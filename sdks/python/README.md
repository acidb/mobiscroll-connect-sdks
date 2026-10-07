# Mobiscroll Connect Python SDK

Python client for [Mobiscroll Connect](https://mobiscroll.com/connect), the calendar connectivity layer for scheduling products — Google Calendar, Microsoft Outlook, Apple Calendar and CalDAV through one API. Backend only — works with your own UI.

[![PyPI](https://img.shields.io/pypi/v/mobiscroll-connect-sdk?label=PyPI)](https://pypi.org/project/mobiscroll-connect-sdk/)

**[PyPI](https://pypi.org/project/mobiscroll-connect-sdk/)** · **[Documentation](https://mobiscroll.com/docs/connect/python-sdk)** · **[Changelog](https://github.com/acidb/mobiscroll-connect-sdks/blob/main/sdks/python/CHANGELOG.md)** · **[Source](https://github.com/acidb/mobiscroll-connect-sdks/tree/main/sdks/python)**

## Features

- **Multi-provider**: Google, Microsoft, Apple, CalDAV
- **OAuth2**: full authorization-code flow
- **Automatic token refresh** with persistence callback
- **Sync and async** clients (`MobiscrollConnectClient` / `mobiscroll_connect.aio.AsyncMobiscrollConnectClient`)
- **Typed responses** via frozen dataclasses
- **Typed exception hierarchy** for HTTP errors
- **Webhook verification** with automatic key fetching and rotation
- **Pagination** helpers (`iter_all` traverses every page)
- **Type-checked** (`py.typed` shipped)

## Installation

```bash
pip install mobiscroll-connect-sdk
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
    lng="es",               # optional: Connect page language, see https://mobiscroll.com/docs/connect/localization#supported-languages
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

**Running more than one instance.** Concurrent calls on the same client share one refresh (a `threading.Lock` on the sync client, an `asyncio.Lock` on the async one). The SDK does not coordinate across processes — separate containers, cluster workers or serverless invocations each refresh from the tokens they hold in memory. Connect accepts concurrent refreshes of the same token, but refreshing with a copy that is two or more refreshes out of date revokes the user's authorization. Persist refreshed tokens to storage every instance reads, and call `client.auth.set_credentials(...)` with the current tokens when a process starts a job or handles a request. See [Refreshing from several instances](https://mobiscroll.com/docs/connect/api/oauth#concurrent-refresh).

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

## Webhooks

```python
# Subscribe to change notifications for a calendar
subscription = client.webhooks.subscribe_webhook("google", "primary")
print(subscription.channel_id, subscription.subscription.resource_id)

# ...persist subscription.channel_id (and subscription.subscription.resource_id
# for Google) so you can unsubscribe later...

# Unsubscribe when you're done
client.webhooks.unsubscribe_webhook(
    "google",
    subscription.channel_id,
    resource_id=subscription.subscription.resource_id,
)
```

### Verify webhook deliveries

Every delivery to your webhook URL is signed. `verify_webhook()` checks the signature and the timestamp, then returns the parsed `WebhookDelivery`, or raises `WebhookVerificationError`. Pass the **raw** request body (`bytes` or `str`): re-serializing a parsed JSON body changes the bytes and every check fails. Headers can be any mapping — Flask, Django and Starlette header objects work as they are.

```python
from flask import request
from mobiscroll_connect import WebhookVerificationError

@app.post("/webhooks/mobiscroll")
def mobiscroll_webhook():
    try:
        delivery = client.webhooks.verify_webhook(request.get_data(), request.headers)
    except WebhookVerificationError as error:
        # 503 lets Connect retry when the keys could not be loaded; 401 is final.
        return "", 503 if error.reason == "no_public_keys" else 401
    handle_delivery(delivery)
    return "", 204
```

On the async client, `await client.webhooks.verify_webhook(await request.body(), request.headers)`.

The public keys are fetched from `https://connect.mobiscroll.com/.well-known/webhook-keys` on the first delivery and cached for the whole process (shared by every client instance) as the endpoint's `Cache-Control` allows. If no signature matches, the keys are fetched again (at most once a minute) before the delivery is rejected, so key rotations need no action on your side.

If your handler cannot make outbound requests, pin the key. `webhook_public_key` is used only when the keys endpoint cannot be reached; it stops working when Mobiscroll retires that key, so you must replace it on every rotation.

```python
client = MobiscrollConnectClient(
    client_id=os.environ["MOBISCROLL_CLIENT_ID"],
    client_secret=os.environ["MOBISCROLL_CLIENT_SECRET"],
    redirect_uri=os.environ["MOBISCROLL_REDIRECT_URI"],
    webhook_public_key=os.environ.get("MOBISCROLL_WEBHOOK_PUBLIC_KEY"),  # whpk_...
)
```

To check against keys you supply, with no fetching, call `verify_webhook_signature(raw_body, headers, ["whpk_..."])`. It raises `WebhookVerificationError` and returns `None`. See [Verifying deliveries](https://mobiscroll.com/docs/connect/api/webhooks#verifying-deliveries).

## Connection management

```python
status = client.auth.get_connection_status()
for provider, accounts in status.connections.items():
    print(f"{provider}: {len(accounts)} account(s)")

    # Google's consent screen lets the user untick the calendar permission and still
    # finish signing in. Such an account is connected but lists no calendars.
    for account in accounts:
        if account.calendar_permission_granted is False:
            print(f"  {account.id} must reconnect and allow calendar access")

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

| Exception                  | HTTP status                 | Extra          |
| -------------------------- | --------------------------- | -------------- |
| `AuthenticationError`      | 401, 403                    | —              |
| `ValidationError`          | 400, 422                    | `.details`     |
| `NotFoundError`            | 404                         | —              |
| `RateLimitError`           | 429                         | `.retry_after` |
| `ServerError`              | 5xx                         | `.status_code` |
| `NetworkError`             | — (transport)               | —              |
| `WebhookVerificationError` | — (from `verify_webhook()`) | `.reason`      |

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
├── webhook_verification.py    — verify_webhook_signature (pure Ed25519 check)
├── _internal/
│   ├── errors.py              — HTTP → exception mapper (shared)
│   ├── payloads.py            — query/payload builders (shared)
│   └── webhook_keys.py        — process-wide webhook key cache (shared)
├── resources/
│   ├── auth.py                — Auth (sync)
│   ├── calendars.py           — Calendars (sync)
│   ├── events.py              — Events (sync)
│   └── webhooks.py            — Webhooks (sync)
└── aio/
    ├── client.py              — AsyncMobiscrollConnectClient
    └── resources.py           — AsyncAuth / AsyncCalendars / AsyncEvents / AsyncWebhooks
```

### Why these choices

- **Frozen dataclasses, not Pydantic.** No third-party runtime dependency for models — matches the "stdlib-only DTOs" approach of the PHP and Node SDKs and keeps install size small. Validation is done where it matters (response parsing, query builders).
- **`httpx` for both sync and async.** Single dependency, identical request API. `requests` would force a separate sync transport.
- **`cryptography` for webhook signatures.** The standard library has no Ed25519.
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
