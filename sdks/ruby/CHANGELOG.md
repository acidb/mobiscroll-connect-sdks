# Changelog

All notable changes to the Mobiscroll Connect Ruby SDK (RubyGems: `mobiscroll-connect`) are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this SDK follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html). SDKs in this monorepo version independently, so this file covers the Ruby SDK only.

## [1.5.1] — 2026-09-22

### Changed

- Updated package metadata. No code or public API changes.

## [1.5.0] — 2026-09-21

### Added

- `webhooks` resource with `subscribe_webhook()` and `unsubscribe_webhook()` for calendar change notifications.
- `sync_state` and `sync_state_updated_at` on `ConnectedAccount`.

## [1.2.0] — 2026-08-27

### Added

- Granted OAuth scopes are reported on the connection status, and a missing scope now raises `CalendarPermissionError`.

## [1.1.0] — 2026-06-03

### Added

- Optional `lng` parameter on the authorization URL, for Connect's localized consent screens.

## [1.0.2] — 2026-05-26

### Added

- First published release: `auth`, `calendars` and `events` resources over Google Calendar, Microsoft Outlook, Apple Calendar and CalDAV.
- Automatic token refresh on 401 with an `on_tokens_refreshed` callback.
- Typed error hierarchy rooted at `Mobiscroll::Connect::Error`.
- Sinatra reference application under `minimal-app/`.

### Changed

- Minimum supported Ruby raised to 3.2.

[1.5.1]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/ruby-v1.5.1
[1.5.0]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/ruby-v1.5.0
[1.2.0]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/ruby-v1.2.0
[1.1.0]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/ruby-v1.1.0
[1.0.2]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/ruby-v1.0.2
