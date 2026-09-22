# Changelog

All notable changes to the Mobiscroll Connect Node.js SDK (npm: `@mobiscroll/connect-sdk`) are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this SDK follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html). SDKs in this monorepo version independently, so this file covers the Node.js SDK only.

## [1.5.1] — 2026-09-22

### Changed

- Package description replaced with the Connect category line, naming the four calendar providers instead of restating the package name.
- Added `homepage`, `repository` (with `directory`) and `bugs`, so npmjs.com links to the product page, the source and the issue tracker. GitHub can now associate the package with the repository.
- Replaced the generic keywords with the shared Connect keyword set.
- README opens with the category line and carries a version badge plus package, documentation, changelog and source links. `CHANGELOG.md` now ships with the package.

## [1.5.0] — 2026-09-21

### Added

- `webhooks` resource with `subscribeWebhook()` and `unsubscribeWebhook()` for calendar change notifications.
- `syncState` on `ConnectedAccount`.

## [1.2.0] — 2026-08-27

### Added

- `description`, `conferenceData` and `lastModified` exposed on `CalendarEvent`.
- Granted OAuth scopes are reported on the connection status, and a missing scope now raises `CalendarPermissionError`.

## [1.1.0] — 2026-06-03

### Added

- Optional `lng` parameter on the authorization URL, for Connect's localized consent screens.

## [1.0.0] — 2026-04-22

### Added

- Initial release: `auth`, `calendars` and `events` resources over Google Calendar, Microsoft Outlook, Apple Calendar and CalDAV.
- Automatic token refresh on 401 with concurrent-request deduplication, and a `tokens` event for persisting refreshed credentials.
- Typed error hierarchy rooted at `MobiscrollConnectError`.

[1.5.1]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/node-v1.5.1
[1.5.0]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/node-v1.5.0
[1.2.0]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/node-v1.2.0
[1.1.0]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/node-v1.1.0
[1.0.0]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/node-v1.0.0
