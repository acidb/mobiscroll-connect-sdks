# Changelog

All notable changes to the Mobiscroll Connect Node.js SDK (npm: `@mobiscroll/connect-sdk`) are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this SDK's major.minor always matches the Connect API's (for example API 1.5.x → SDK 1.5.x): each release bumps only the patch, including releases with breaking changes. Patch numbers are independent per SDK, so this file covers the Node.js SDK only.

## [Unreleased]

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
