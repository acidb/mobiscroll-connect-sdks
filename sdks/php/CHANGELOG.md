# Changelog

All notable changes to the Mobiscroll Connect PHP SDK (Packagist: `mobiscroll/connect-php`) are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this SDK's major.minor always matches the Connect API's (for example API 1.5.x → SDK 1.5.x): each release bumps only the patch, including releases with breaking changes. Patch numbers are independent per SDK, so this file covers the PHP SDK only.

## [1.6.1] — 2026-10-22

### Added

- `auth()->revoke(?string $token = null)` for `POST /api/oauth/revoke`: revokes the user's Connect access and refresh tokens (the whole grant).
  - Without an argument it revokes the stored refresh token, or the access token when there is none, then clears the stored credentials.
  - It does not disconnect calendar accounts or stop webhooks; call `disconnect()` first to remove a user entirely.
- `generateAuthUrl()` accepts a `theme` option (`'light'`, `'dark'` or `'auto'`) that sets the color scheme of the Connect pages.
- `Calendar` objects now include `account` (the connected account the calendar belongs to) and `readOnly` (`true` when the connection cannot write to it).

### Fixed

- `verifyWebhook()` accepts deliveries whose events include a conference. Before, it rejected the whole delivery with reason `invalid_payload`.
- On those events:
  - `$conference` is the join URL, or `null` when the delivered object has no `url`.
  - `$conferenceData` is the REST shape returned by `events()->list()`: the delivery's own `conferenceData` (Connect 1.6.1 and later), or for an earlier delivery, one built from the delivered `conference` object.
- A `conference` that is neither a string, an object nor `null` fails with reason `invalid_payload`.
- `verifyWebhook()`, documentation only (no change in behaviour): the doc comments of `verifyWebhook()` and `WebhookVerificationError` said to answer a failed verification with a `4xx`. They now say:
  - `503` when `reason` is `no_public_keys`: the public keys could not be loaded, and Connect retries the delivery.
  - `401` for every other reason: Connect does not retry.
  - The README example already answered this way.
  - If your handler answers a `4xx` for `no_public_keys`, Connect drops that delivery; answer `503` instead.
- `events()->create()` and `events()->update()` now return the written event. They threw `ServerError` on every successful write, because they expected the event inside an `event` field.
- The default `scope` of `generateAuthUrl()` is now `'read-write'`. It was `'calendar'`, which is not a Connect scope; Connect treated it as `read-write`, so the authorization behaves the same.
- Documentation only: the doc comments of `create()` and `update()` now list the fields the API accepts: `attendees` as email strings, `conference`, `conferenceData` (`autoGenerate`), `recurrence`, `custom`, `availability`, `privacy` and `status`.

## [1.6.0] — 2026-10-08

### Added

- `webhooks()->verifyWebhook()` checks a delivery's Ed25519 signature and timestamp and returns the parsed `WebhookDelivery`, or throws `WebhookVerificationError`. It fetches and caches the public keys from `/.well-known/webhook-keys` and re-fetches them once before rejecting, so key rotations need no action.
- `webhookKeyCache` client option: a PSR-16 cache that shares the fetched keys across PHP-FPM requests and workers.
- `webhookPublicKey` client option: a pinned key, used only when the keys endpoint cannot be reached.
- `WebhookVerifier::verifyWebhookSignature()` for verifying against keys you supply, with no fetching.
- Requires the `sodium` extension (bundled with PHP) and `psr/simple-cache`.

## [1.5.1] — 2026-09-22

### Changed

- Updated package metadata. No code or public API changes.

## [1.5.0] — 2026-09-21

### Added

- `Webhooks` resource with `subscribeWebhook()` and `unsubscribeWebhook()` for calendar change notifications.
- `syncState` on `ConnectedAccount`.

## [1.2.0] — 2026-08-27

### Added

- `description`, `conferenceData` and `lastModified` exposed on `CalendarEvent`.
- Granted OAuth scopes are reported on the connection status, and a missing scope now raises `CalendarPermissionError`.

## [1.1.1] — 2026-06-03

### Added

- Optional `lng` parameter on the authorization URL, for Connect's localized consent screens.

## [1.1.0] — 2026-05-18

### Removed

- `limit` field on `ConnectionStatusResponse`, for parity with the Node SDK.

## [1.0.1] — 2026-04-14

### Changed

- Method signatures aligned with the other Connect SDKs; test coverage extended.

## [1.0.0] — 2026-04-10

### Added

- Initial release: `Auth`, `Calendars` and `Events` resources over Google Calendar, Microsoft Outlook, Apple Calendar and CalDAV.
- Automatic token refresh on 401 with an `onTokensRefreshed` callback.
- Typed exception hierarchy rooted at `MobiscrollConnectException`.

[1.5.1]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/php-v1.5.1
[1.5.0]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/php-v1.5.0
[1.2.0]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/php-v1.2.0
[1.1.1]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/php-v1.1.1
[1.1.0]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/php-v1.1.0
[1.0.1]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/php-v1.0.1
[1.0.0]: https://github.com/acidb/mobiscroll-connect-sdks/releases/tag/php-v1.0.0
