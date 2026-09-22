# Changelog

The seven Connect SDKs release independently, so each one keeps its own changelog. This file is an index and a record of the changes that landed across all of them at once.

| SDK | Changelog | Registry | Latest |
|-----|-----------|----------|--------|
| Node.js | [sdks/node/CHANGELOG.md](sdks/node/CHANGELOG.md) | [npm](https://www.npmjs.com/package/@mobiscroll/connect-sdk) | 1.5.0 |
| Python | [sdks/python/CHANGELOG.md](sdks/python/CHANGELOG.md) | [PyPI](https://pypi.org/project/mobiscroll-connect-sdk/) | 1.5.0 |
| PHP | [sdks/php/CHANGELOG.md](sdks/php/CHANGELOG.md) | [Packagist](https://packagist.org/packages/mobiscroll/connect-php) | 1.5.0 |
| .NET | [sdks/dotnet/CHANGELOG.md](sdks/dotnet/CHANGELOG.md) | [NuGet](https://www.nuget.org/packages/Mobiscroll.Connect) | 1.5.0 |
| Java | [sdks/java/CHANGELOG.md](sdks/java/CHANGELOG.md) | [Maven Central](https://central.sonatype.com/artifact/com.mobiscroll/connect-sdk) | 1.5.0 |
| Go | [sdks/go/CHANGELOG.md](sdks/go/CHANGELOG.md) | [pkg.go.dev](https://pkg.go.dev/github.com/acidb/mobiscroll-connect-sdks/sdks/go) | 1.5.0 |
| Ruby | [sdks/ruby/CHANGELOG.md](sdks/ruby/CHANGELOG.md) | [RubyGems](https://rubygems.org/gems/mobiscroll-connect) | 1.5.0 |

## Cross-SDK releases

Changes that shipped in every SDK on the same day. Individual SDKs may carry additional changes in the same version — see their own changelogs.

### 1.5.0 — 2026-09-21

- Webhooks resource for subscribing to and unsubscribing from calendar change notifications.
- `syncState` on `ConnectedAccount`.

Versions 1.3.0 and 1.4.0 were never released; all seven SDKs went from 1.2.0 to 1.5.0 to bring the version numbers back into line with the Connect API.

### 1.2.0 — 2026-08-27

- Granted OAuth scopes reported on the connection status, with a dedicated permission error when a scope is missing.
- `description`, `conferenceData` and `lastModified` on `CalendarEvent` (Ruby already exposed these).

### 1.1.x — 2026-06-03

- Optional `lng` parameter on the authorization URL, for Connect's localized consent screens. Shipped as 1.1.0 in Node, Java, Go and Ruby, and as 1.1.1 in Python, PHP and .NET.

## Version history

Each SDK entered the monorepo at its own pace, so the early version numbers do not line up:

| SDK | First release | Versions |
|-----|---------------|----------|
| PHP | 2026-04-10 | 1.0.0, 1.0.1, 1.1.0, 1.1.1, 1.2.0, 1.5.0 |
| Node.js | 2026-04-22 | 1.0.0, 1.1.0, 1.2.0, 1.5.0 |
| .NET | 2026-04-23 | 1.0.0, 1.1.0, 1.1.1, 1.2.0, 1.5.0 |
| Python | 2026-04-29 | 1.0.0, 1.0.1, 1.1.0, 1.1.1, 1.2.0, 1.5.0 |
| Go | 2026-05-18 | 1.0.0, 1.1.0, 1.2.0, 1.5.0 |
| Java | 2026-05-20 | 1.0.0, 1.1.0, 1.2.0, 1.5.0 |
| Ruby | 2026-05-26 | 1.0.2, 1.1.0, 1.2.0, 1.5.0 |
