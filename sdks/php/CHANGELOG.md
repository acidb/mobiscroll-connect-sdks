# Changelog

All notable changes to the Mobiscroll Connect PHP SDK (Packagist: `mobiscroll/connect-php`) are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this SDK follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html). SDKs in this monorepo version independently, so this file covers the PHP SDK only.

## [1.5.1] — 2026-09-22

### Changed

- Package description replaced with the Connect category line, naming the four calendar providers instead of restating the package name.
- Added `keywords`, `homepage`, and `support.issues` / `support.source` / `support.docs` to `composer.json`, so the Packagist sidebar links back to mobiscroll.com and the SDK repository.
- Removed "seamless" and the "multiple providers" breadth claim from the README opener, which Packagist renders as the package page body.

## [1.5.0] — 2026-09-21

### Added

- `Webhooks` resource with `subscribeWebhook()` and `unsubscribeWebhook()` for calendar change notifications.
- `syncState` on `ConnectedAccount`.

## [1.2.0] — 2026-08-27

### Added

- `description`, `conferenceData` and `lastModified` exposed on `CalendarEvent`.
- Granted OAuth scopes are reported on the connection status, and a missing scope now raises `CalendarPermissionError`.

## [1.1.1] — 2026-06-03

### Added

- Optional `lng` parameter on the authorization URL, for Connect's localized consent screens.

## [1.1.0] — 2026-05-18

### Removed

- `limit` field on `ConnectionStatusResponse`, for parity with the Node SDK.

## [1.0.1] — 2026-04-14

### Changed

- Method signatures aligned with the other Connect SDKs; test coverage extended.

## [1.0.0] — 2026-04-10

### Added

- Initial release: `Auth`, `Calendars` and `Events` resources over Google Calendar, Microsoft Outlook, Apple Calendar and CalDAV.
- Automatic token refresh on 401 with an `onTokensRefreshed` callback.
- Typed exception hierarchy rooted at `MobiscrollConnectException`.

[1.5.1]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/php-v1.5.1
[1.5.0]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/php-v1.5.0
[1.2.0]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/php-v1.2.0
[1.1.1]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/php-v1.1.1
[1.1.0]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/php-v1.1.0
[1.0.1]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/php-v1.0.1
[1.0.0]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/php-v1.0.0
