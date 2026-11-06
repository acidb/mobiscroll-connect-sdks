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
- `ConferenceData`, the type of `CalendarEvent.getConferenceData()`, with `getProvider()`, `getUrl()`, `getAutoGenerate()` and `getOriginal()`. `getConferenceData()` returned a `Map<String, Object>`; read the provider's own keys, such as `entryPoints`, from `getOriginal()`.
- `CalendarEvent.getChangeType()` (`"created"`, `"updated"` or `"deleted"`), set on the events of a `verifyWebhook()` delivery. `WebhookDelivery.getEvents()` now returns `List<CalendarEvent>`, and `WebhookEvent` is deprecated and will be removed in 2.0.0.

### Fixed

- `verifyWebhook()` accepts deliveries whose events include a conference, with `getConference()` as the join URL. It rejected the whole delivery with reason `INVALID_PAYLOAD`, because Connect sent `conference` as an object before 1.6.1.
- Doc comments of `verifyWebhook()` and `WebhookVerificationException`, no code change: answer a failed verification with `503` when the reason is `NO_PUBLIC_KEYS`, so Connect retries the delivery, and with `401` for every other reason. They said to answer with a `4xx`, and Connect drops a delivery answered with a `4xx`.

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
