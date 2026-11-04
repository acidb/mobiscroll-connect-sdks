# Changelog

All notable changes to the Mobiscroll Connect Go SDK (module: `github.com/acidb/mobiscroll-connect-sdks/sdks/go`) are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this SDK's major.minor always matches the Connect API's (for example API 1.5.x → SDK 1.5.x): each release bumps only the patch, including releases with breaking changes. Patch numbers are independent per SDK, so this file covers the Go SDK only. Release tags use the `sdks/go/vX.Y.Z` form the Go module proxy requires.

## [1.6.1] — 2026-10-22

### Added

- `Auth().Revoke(ctx, token)` for `POST /api/oauth/revoke`: revokes the user's Connect access and refresh tokens (the whole grant).
  - With an empty `token` it revokes the stored refresh token, or the access token when there is none, then clears the stored credentials.
  - It does not disconnect calendar accounts or stop webhooks; call `Disconnect` first to remove a user entirely.
- `AuthURLParams.Theme` (`"light"`, `"dark"` or `"auto"`) sets the color scheme of the Connect pages.
- `Calendar` objects now include `Account` (the connected account the calendar belongs to) and `ReadOnly` (`true` when the connection cannot write to it).
- `EventCreateData` and `EventUpdateData` now include `Conference` (a meeting URL) and `ConferenceData` (`*EventConferenceData`; `AutoGenerate: mobiscroll.Ptr(true)` creates a meeting link).

### Fixed

- `VerifyWebhook` accepts deliveries whose events include a conference. Before, it rejected the whole delivery with reason `invalid_payload`.
- On those events:
  - `WebhookEvent.Conference` is the join URL, or an empty string when the delivered object has no `url`.
  - `ConferenceData` is the REST shape returned by the events endpoints: the delivery's own `conferenceData` (Connect 1.6.1 and later), or for an earlier delivery, one built from the delivered `conference` object.
- A `conference` that is neither a string, an object nor `null` fails with reason `invalid_payload`.
- `VerifyWebhook`, documentation only (no change in behaviour): the doc comments of `VerifyWebhook` and `WebhookVerificationError` said to answer a failed verification with a `4xx`. They now say:
  - `503` when `Reason` is `WebhookNoPublicKeys`: the public keys could not be loaded, and Connect retries the delivery.
  - `401` for every other reason: Connect does not retry.
  - If your handler answers a `4xx` for `WebhookNoPublicKeys`, Connect drops that delivery; answer `503` instead.

## [1.6.0] — 2026-10-08

### Added

- `Webhooks().VerifyWebhook()` checks a delivery's Ed25519 signature and timestamp and returns the parsed `*WebhookDelivery`, or a `*WebhookVerificationError`. It fetches and caches the public keys from `/.well-known/webhook-keys` and re-fetches them once before rejecting, so key rotations need no action.
- `WithWebhookPublicKey` client option: a pinned key, used only when the keys endpoint cannot be reached.
- `VerifyWebhookSignature()` for verifying against keys you supply, with no fetching.

## [1.5.1] — 2026-09-22

### Changed

- Updated package metadata. No code or public API changes.

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
