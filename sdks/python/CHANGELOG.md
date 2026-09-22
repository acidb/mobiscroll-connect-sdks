# Changelog

All notable changes to the Mobiscroll Connect Python SDK (PyPI: `mobiscroll-connect-sdk`) are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this SDK follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html). SDKs in this monorepo version independently, so this file covers the Python SDK only.

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
