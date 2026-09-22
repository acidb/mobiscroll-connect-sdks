# Changelog

All notable changes to the Mobiscroll Connect .NET SDK (NuGet: `Mobiscroll.Connect`) are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this SDK follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html). SDKs in this monorepo version independently, so this file covers the .NET SDK only.

## [1.5.0] — 2026-09-21

### Added

- `Webhooks` resource with `SubscribeWebhookAsync()` and `UnsubscribeWebhookAsync()` for calendar change notifications.
- `SyncState` on `ConnectedAccount`.

## [1.2.0] — 2026-08-27

### Added

- `Description`, `ConferenceData` and `LastModified` exposed on `CalendarEvent`.
- Granted OAuth scopes are reported on the connection status, and a missing scope now raises `CalendarPermissionException`.

## [1.1.1] — 2026-06-03

### Added

- Optional `lng` parameter on the authorization URL, for Connect's localized consent screens.

## [1.1.0] — 2026-05-18

### Fixed

- `ConnectionStatusResponse` now matches the Node SDK wire format.

## [1.0.0] — 2026-04-23

### Added

- Initial release: `Auth`, `Calendars` and `Events` resources over Google Calendar, Microsoft Outlook, Apple Calendar and CalDAV.
- Automatic token refresh on 401, deduplicated across concurrent requests, with an `OnTokensRefreshed` callback.
- `AddMobiscrollConnect()` extension for ASP.NET Core dependency injection.
- Typed exception hierarchy rooted at `MobiscrollConnectException`.

[1.5.0]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/dotnet-v1.5.0
[1.2.0]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/dotnet-v1.2.0
[1.1.1]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/dotnet-v1.1.1
[1.1.0]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/dotnet-v1.1.0
[1.0.0]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/dotnet-v1.0.0
