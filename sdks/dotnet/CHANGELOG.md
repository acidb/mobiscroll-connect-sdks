# Changelog

All notable changes to the Mobiscroll Connect .NET SDK (NuGet: `Mobiscroll.Connect`) are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this SDK's major.minor always matches the Connect API's (for example API 1.5.x → SDK 1.5.x): each release bumps only the patch, including releases with breaking changes. Patch numbers are independent per SDK, so this file covers the .NET SDK only.

## [1.6.1] — 2026-10-22

### Added

- `Auth.RevokeAsync(token?)` for `POST /api/oauth/revoke`: revokes the user's Connect access and refresh tokens.
- `AuthorizeParams.Theme` (`"light"`, `"dark"` or `"auto"`) sets the color scheme of the Connect pages.
- `Calendar` objects now include `Account` (the connected account the calendar belongs to) and `ReadOnly` (`true` when the connection cannot write to it).
- `EventCreateData` and `EventUpdateData` now include `Conference` (a meeting URL) and `ConferenceData` (an `EventConferenceData`; `AutoGenerate = true` creates a meeting link).

### Fixed

- `VerifyWebhookAsync()` accepts deliveries whose events include a conference: `Conference` is the join URL of the delivered `conference` object, and `ConferenceData` is the delivery's `conferenceData`, or one built from that object when the delivery has none. It rejected the whole delivery with reason `InvalidPayload`, because a delivery carries `conference` as an object.
- A `provider` value this SDK version does not know, sent by a newer Connect, is read as the new `Provider.Unknown`. It failed the whole `Events.ListAsync()` response or `VerifyWebhookAsync()` delivery.
- Doc comments of `VerifyWebhookAsync()` and `WebhookVerificationException`, no code change: answer a failed verification with `503` when the reason is `NoPublicKeys`, so Connect retries the delivery, and with `401` for every other reason. They said to answer with a `4xx`, and Connect drops a delivery answered with a `4xx`.

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
