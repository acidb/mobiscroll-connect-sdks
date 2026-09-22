# Changelog

All notable changes to the Mobiscroll Connect Go SDK (module: `github.com/acidb/mobiscroll-connect-sdks/sdks/go`) are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this SDK follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html). SDKs in this monorepo version independently, so this file covers the Go SDK only. Release tags use the `sdks/go/vX.Y.Z` form the Go module proxy requires.

## [1.5.1] — 2026-09-22

### Changed

- Package doc comment rewritten so the pkg.go.dev synopsis leads with what the SDK does — calendars, providers, OAuth and webhooks — instead of the cross-SDK parity note, which now sits below it.
- README opens with the category line and carries a pkg.go.dev badge plus package, documentation, changelog and source links.

## [1.5.0] — 2026-09-21

### Added

- `Webhooks()` resource with `SubscribeWebhook()` and `UnsubscribeWebhook()` for calendar change notifications.
- `SyncState` on `ConnectedAccount`.

## [1.2.0] — 2026-08-27

### Added

- `Description`, `ConferenceData` and `LastModified` exposed on `CalendarEvent`.
- Granted OAuth scopes are reported on the connection status, and a missing scope now returns `*CalendarPermissionError`.

## [1.1.0] — 2026-06-03

### Added

- Optional `Lng` parameter on the authorization URL, for Connect's localized consent screens.

## [1.0.0] — 2026-05-18

### Added

- Initial release: `Auth()`, `Calendars()` and `Events()` resources over Google Calendar, Microsoft Outlook, Apple Calendar and CalDAV.
- Automatic token refresh on 401, deduplicated with `singleflight`, retried once.
- Typed errors satisfying the `MobiscrollError` interface.

[1.5.1]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/sdks%2Fgo%2Fv1.5.1
[1.5.0]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/sdks%2Fgo%2Fv1.5.0
[1.2.0]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/sdks%2Fgo%2Fv1.2.0
[1.1.0]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/sdks%2Fgo%2Fv1.1.0
[1.0.0]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/sdks%2Fgo%2Fv1.0.0
