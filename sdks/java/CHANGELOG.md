# Changelog

All notable changes to the Mobiscroll Connect Java SDK (Maven Central: `com.mobiscroll:connect-sdk`) are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this SDK follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html). SDKs in this monorepo version independently, so this file covers the Java SDK only.

## [1.5.1] — 2026-09-22

### Changed

- Updated package metadata. No code or public API changes.

## [1.5.0] — 2026-09-21

### Added

- `Webhooks` resource with `subscribeWebhook()` and `unsubscribeWebhook()` for calendar change notifications.
- `syncState` on `ConnectedAccount`.

### Changed

- Upgraded `central-publishing-maven-plugin` to 0.11.0.

## [1.2.0] — 2026-08-27

### Added

- `description`, `conferenceData` and `lastModified` exposed on `CalendarEvent`.
- Granted OAuth scopes are reported on the connection status, and a missing scope now raises `CalendarPermissionException`.

## [1.1.0] — 2026-06-03

### Added

- Optional `lng` parameter on the authorization URL, for Connect's localized consent screens.

## [1.0.0] — 2026-05-20

### Added

- Initial release: `Auth`, `Calendars` and `Events` resources over Google Calendar, Microsoft Outlook, Apple Calendar and CalDAV.
- Automatic token refresh on 401 with retry, and a typed exception hierarchy rooted at `MobiscrollConnectException`.
- Spring Boot reference application under `minimal-app/`.

[1.5.1]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/java-v1.5.1
[1.5.0]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/java-v1.5.0
[1.2.0]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/java-v1.2.0
[1.1.0]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/java-v1.1.0
[1.0.0]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/java-v1.0.0
