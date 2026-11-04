# Changelog

All notable changes to the Mobiscroll Connect Java SDK (Maven Central: `com.mobiscroll:connect-sdk`) are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this SDK's major.minor always matches the Connect API's (for example API 1.5.x → SDK 1.5.x): each release bumps only the patch, including releases with breaking changes. Patch numbers are independent per SDK, so this file covers the Java SDK only.

## [1.6.1] — 2026-10-22

### Added

- `auth().revoke()` and `auth().revoke(String token)` for `POST /api/oauth/revoke`: revoke the user's Connect access and refresh tokens (the whole grant).
  - The overload without arguments revokes the stored refresh token, or the access token when there is none, then clears the stored credentials.
  - It does not disconnect calendar accounts or stop webhooks; call `disconnect()` first to remove a user entirely.
- `AuthUrlParams.builder().theme(...)` (`"light"`, `"dark"` or `"auto"`) sets the color scheme of the Connect pages.
- `Calendar` objects now include `getAccount()` (the connected account the calendar belongs to) and `getReadOnly()` (`true` when the connection cannot write to it).
- The `EventCreateData` and `EventUpdateData` builders now include `conference(String)` (a meeting URL) and `conferenceData(EventConferenceData)` (`autoGenerate(true)` creates a meeting link).

### Fixed

- `verifyWebhook()` accepts deliveries whose events include a conference. Before, it rejected the whole delivery with reason `INVALID_PAYLOAD`.
- On those events:
  - `WebhookEvent.getConference()` is the join URL, or `null` when the delivered object has no `url`.
  - `getConferenceData()` is the REST shape returned by the events endpoints: the delivery's own `conferenceData` (Connect 1.6.1 and later), or for an earlier delivery, one built from the delivered `conference` object.
- A `conference` that is a number or a boolean now fails with reason `INVALID_PAYLOAD`. Before, it was turned into a string.
- `verifyWebhook()`, documentation only (no change in behaviour): the doc comments of `verifyWebhook()` and `WebhookVerificationException` said to answer a failed verification with a `4xx`. They now say:
  - `503` when `getReason()` is `NO_PUBLIC_KEYS`: the public keys could not be loaded, and Connect retries the delivery.
  - `401` for every other reason: Connect does not retry.
  - The README example already answered this way.
  - If your handler answers a `4xx` for `NO_PUBLIC_KEYS`, Connect drops that delivery; answer `503` instead.

## [1.6.0] — 2026-10-08

### Added

- `webhooks().verifyWebhook()` checks a delivery's Ed25519 signature and timestamp and returns the parsed `WebhookDelivery`, or throws `WebhookVerificationException`. It fetches and caches the public keys from `/.well-known/webhook-keys` and re-fetches them once before rejecting, so key rotations need no action.
- `webhookPublicKey` config option: a pinned key, used only when the keys endpoint cannot be reached.
- `WebhookVerifier.verifyWebhookSignature()` for verifying against keys you supply, with no fetching.

### Changed

- **Requires Java 17** (was 11). Webhook verification uses the JDK's built-in Ed25519 support.

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
