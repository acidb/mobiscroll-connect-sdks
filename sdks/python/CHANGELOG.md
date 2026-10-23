# Changelog

All notable changes to the Mobiscroll Connect Python SDK (PyPI: `mobiscroll-connect-sdk`) are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this SDK's major.minor always matches the Connect API's (for example API 1.5.x → SDK 1.5.x): each release bumps only the patch, including releases with breaking changes. Patch numbers are independent per SDK, so this file covers the Python SDK only.

## [1.6.1] — 2026-10-22

### Added

- New: `auth.revoke(token=None)` on the sync and async clients revokes the user's Connect access and refresh tokens (the whole grant) and returns a `RevokeResponse`. Without an argument it revokes the stored refresh token, else the access token, then clears the stored credentials. It does not disconnect calendar accounts or stop webhooks; call `disconnect()` first to remove a user entirely.
- New: `Calendar` has `account` (the connected account the calendar belongs to) and `read_only` (`True` when the connection cannot write to it).
- New: `events.create()` and `events.update()` map the `conference_data` key to the API's `conferenceData`, so `{"autoGenerate": True}` creates a provider meeting link; `conference` takes a meeting URL.
- New: `generate_auth_url()` takes `theme` (`"light"`, `"dark"` or `"auto"`) for the Connect pages, on the sync and async clients.

### Fixed

- `WebhookEvent.conference` is now a `WebhookConference` (`url`, `provider`, `meeting_provider`, `data`), matching the object deliveries contain; it was typed as a string and held the raw dictionary. `CalendarEvent.conference` from `events.list()` stays the join-URL string.
- Corrected the guidance for a failed `verify_webhook()`: respond `503` when `reason` is `"no_public_keys"` so Connect retries the delivery, and `401` otherwise.
- `events.create()` and `events.update()` on the sync and async clients now return the written event; they raised `ServerError` on every successful write because they expected the event inside an `event` field.
- The default `scope` of `generate_auth_url()` is now `"read-write"` on the sync and async clients. It was `"calendar"`, which is not a Connect scope; Connect treated it as `read-write`, so the authorization behaves the same.

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
