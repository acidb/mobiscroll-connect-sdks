# Changelog

All notable changes to the Mobiscroll Connect Python SDK (PyPI: `mobiscroll-connect-sdk`) are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this SDK's major.minor always matches the Connect API's (for example API 1.5.x → SDK 1.5.x): each release bumps only the patch, including releases with breaking changes. Patch numbers are independent per SDK, so this file covers the Python SDK only.

## [1.6.1] — 2026-11-10

### Fixed

- We added the missing `auth.revoke(token=None)` for `POST /api/oauth/revoke`, on the sync and async clients: it revokes the user's Connect access and refresh tokens.
- We fixed `generate_auth_url()` not accepting `theme`, on the sync and async clients. It now accepts `"light"`, `"dark"` or `"auto"`, which sets the color scheme of the Connect pages.
- We added the missing `account` (the connected account the calendar belongs to) and `read_only` (`True` when the connection cannot write to it) to `Calendar` objects.
- We added the missing `conference_data` key to `events.create()` and `events.update()`, which send it as `conferenceData`, so `{"autoGenerate": True}` creates a meeting link. `conference` takes a meeting URL.
- `WebhookEvent.conference` is annotated `Mapping[str, Any] | None`, the dict a `verify_webhook()` delivery carries (`url`, `provider`, `meetingProvider`, `data`). It was annotated `str | None`.
- `events.create()` and `events.update()` now return the written event, on the sync and async clients. They raised `ServerError` on every successful write, because they expected the event inside an `event` field.
- The default `scope` of `generate_auth_url()` is now `"read-write"`, on the sync and async clients. It was `"calendar"`, which is not a Connect scope; Connect treated it as `read-write`, so the authorization behaves the same.

## [1.6.0] — 2026-10-08

### Added

- `webhooks.verify_webhook()` on the sync and async clients checks a delivery's Ed25519 signature and timestamp and returns the parsed `WebhookDelivery`, or raises `WebhookVerificationError`. It fetches and caches the public keys from `/.well-known/webhook-keys` and re-fetches them once before rejecting, so key rotations need no action.
- `webhook_public_key` client option: a pinned key, used only when the keys endpoint cannot be reached.
- `verify_webhook_signature()` for verifying against keys you supply, with no fetching.

### Changed

- New runtime dependency: `cryptography>=3.4`, for Ed25519.

## [1.5.1] — 2026-09-22

### Changed

- Updated package metadata. No code or public API changes.

## [1.5.0] — 2026-09-21

### Added

- `webhooks` resource with `subscribe_webhook()` and `unsubscribe_webhook()` for calendar change notifications, on both the sync and async clients.
- `sync_state` on `ConnectedAccount`.

## [1.2.0] — 2026-08-27

### Added

- `description`, `conference_data` and `last_modified` exposed on `CalendarEvent`.
- Granted OAuth scopes are reported on the connection status, and a missing scope now raises `CalendarPermissionError`.

## [1.1.1] — 2026-06-03

### Added

- Optional `lng` parameter on the authorization URL, for Connect's localized consent screens.

## [1.1.0] — 2026-05-18

### Fixed

- `ConnectionStatusResponse` and `CalendarEvent` now match the Node SDK wire format.

## [1.0.1] — 2026-05-12

### Changed

- Published to PyPI as `mobiscroll-connect-sdk`. The importable package name remains `mobiscroll_connect`.

## [1.0.0] — 2026-04-29

### Added

- Initial release: `auth`, `calendars` and `events` resources over Google Calendar, Microsoft Outlook, Apple Calendar and CalDAV.
- Async client (`mobiscroll_connect.aio`) with the same public surface as the sync client.
- Automatic token refresh on 401 with lock-based deduplication and an `on_tokens_refreshed` callback.
- Typed exception hierarchy rooted at `MobiscrollConnectError`.

[1.5.1]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/python-v1.5.1
[1.5.0]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/python-v1.5.0
[1.2.0]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/python-v1.2.0
[1.1.1]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/python-v1.1.1
[1.1.0]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/python-v1.1.0
[1.0.1]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/python-v1.0.1
[1.0.0]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/python-v1.0.0
