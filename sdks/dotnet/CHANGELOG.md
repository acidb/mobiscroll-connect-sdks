# Changelog

All notable changes to the Mobiscroll Connect .NET SDK (NuGet: `Mobiscroll.Connect`) are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this SDK's major.minor always matches the Connect API's (for example API 1.5.x → SDK 1.5.x): each release bumps only the patch, including releases with breaking changes. Patch numbers are independent per SDK, so this file covers the .NET SDK only.

## [1.6.1] — 2026-10-22

### Added

- `Auth.RevokeAsync(token?)` revokes the user's Connect access and refresh tokens (the whole grant) and returns a `RevokeResponse`. Without an argument it revokes the stored refresh token, else the access token, then clears the stored credentials. It does not disconnect calendar accounts or stop webhooks; call `DisconnectAsync()` first to remove a user entirely.
- `Calendar` has `Account` (the connected account the calendar belongs to) and `ReadOnly` (`true` when the connection cannot write to it).
- `EventCreateData` and `EventUpdateData` take `Conference` (a meeting URL) and `ConferenceData` (an `EventConferenceData`; `AutoGenerate = true` creates a provider meeting link).
- `AuthorizeParams.Theme` (`"light"`, `"dark"` or `"auto"`) sets the color scheme of the Connect pages.

### Fixed

- `VerifyWebhookAsync()` now accepts deliveries whose events include a conference; they were rejected with reason `InvalidPayload`. `WebhookEvent.Conference` is a `WebhookConference` (`Url`, `Provider`, `MeetingProvider`, `Data`), matching the object deliveries contain. `WebhookEvent` no longer derives from `CalendarEvent`, whose `Conference` stays the join-URL string `Events.ListAsync()` returns; it keeps the same properties.
- Corrected the guidance for a failed `VerifyWebhookAsync()`: respond `503` when `Reason` is `NoPublicKeys` so Connect retries the delivery, and `401` otherwise.

## [1.6.0] — 2026-10-08

### Added

- `Webhooks.VerifyWebhookAsync()` checks a delivery's Ed25519 signature and timestamp and returns the parsed `WebhookDelivery`, or throws `WebhookVerificationException`. It fetches and caches the public keys from `/.well-known/webhook-keys` and re-fetches them once before rejecting, so key rotations need no action.
- `WebhookPublicKey` config option: a pinned key, used only when the keys endpoint cannot be reached.
- `WebhookVerifier.VerifyWebhookSignature()` for verifying against keys you supply, with no fetching.

### Changed

- New dependency: `NSec.Cryptography` (with the native `libsodium` package) for Ed25519, which .NET 8 does not provide.
- `CalendarEvent` is no longer sealed; `WebhookEvent` extends it.

## [1.5.1] — 2026-09-22

### Changed

- Updated package metadata. No code or public API changes.

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

[1.5.1]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/dotnet-v1.5.1
[1.5.0]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/dotnet-v1.5.0
[1.2.0]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/dotnet-v1.2.0
[1.1.1]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/dotnet-v1.1.1
[1.1.0]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/dotnet-v1.1.0
[1.0.0]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/dotnet-v1.0.0
