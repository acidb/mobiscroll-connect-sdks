# Changelog

All notable changes to the Mobiscroll Connect Node.js SDK (npm: `@mobiscroll/connect-sdk`) are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this SDK's major.minor always matches the Connect API's (for example API 1.5.x → SDK 1.5.x): each release bumps only the patch, including releases with breaking changes. Patch numbers are independent per SDK, so this file covers the Node.js SDK only.

## [1.6.1] — 2026-10-22

### Added

- `auth.revoke(token?)` for `POST /api/oauth/revoke`: revokes the user's Connect access and refresh tokens (the whole grant).
  - Without an argument it revokes the stored refresh token, or the access token when there is none, then clears the stored credentials.
  - It does not disconnect calendar accounts or stop webhooks; call `disconnect()` first to remove a user entirely.
- `generateAuthUrl()` accepts a `theme` option (`'light'`, `'dark'` or `'auto'`) that sets the color scheme of the Connect pages.
- `ConferenceData` declares `url` and `original`, which the API returns from 1.6.1.

### Fixed

- `verifyWebhook()` return type (types only, no change at runtime):
  - `start` and `end` are typed `string`, the ISO 8601 strings the SDK returns. They were typed `Date`, so calling `Date` methods on them threw.
  - `lastModified` is always present.
  - `original` is optional: deleted Microsoft, Apple and CalDAV events arrive without it.
  - `calendarId` is optional: deleted Microsoft, Apple and CalDAV events in deliveries sent before Connect 1.6.1 arrive without it.
- `verifyWebhook()`, documentation only (no change in behaviour): the doc comments of `verifyWebhook()` and `WebhookVerificationError` said to answer a failed verification with a `4xx`. They now say:
  - `503` when `reason` is `no_public_keys`: the public keys could not be loaded, and Connect retries the delivery.
  - `401` for every other reason: Connect does not retry.
  - The README example already answered this way.
  - If your handler answers a `4xx` for `no_public_keys`, Connect drops that delivery; answer `503` instead.
- `start` and `end` on `CalendarEvent` from `events.list()`, `events.create()` and `events.update()` are typed `string` too, for the same reason.
- The `EventCreateData` and `EventUpdateData` types were missing `conference` (a meeting URL) and `conferenceData`, so TypeScript rejected `{ autoGenerate: true }` in an object literal. The SDK already sent both fields at runtime.
- The `Calendar` type was missing `account` (the connected account the calendar belongs to) and `readOnly` (`true` when the connection cannot write to it). Both were already present at runtime.

## [1.6.0] — 2026-10-08

### Added

- `webhooks.verifyWebhook()` checks a delivery's Ed25519 signature and timestamp and returns the parsed `WebhookDelivery`, or throws `WebhookVerificationError`. It fetches and caches the public keys from `/.well-known/webhook-keys` and re-fetches them once before rejecting, so key rotations need no action.
- `webhookPublicKey` config option: a pinned key, used only when the keys endpoint cannot be reached.
- `verifyWebhookSignature()` for verifying against keys you supply, with no fetching.

## [1.5.1] — 2026-09-22

### Changed

- Updated package metadata. No code or public API changes.

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
