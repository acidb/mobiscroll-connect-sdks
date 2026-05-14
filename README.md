# Mobiscroll Connect SDKs

Official client SDKs for the [Mobiscroll Connect](https://mobiscroll.com/connect) API. This monorepo contains five language SDKs that share a common architecture (Auth, Calendars, Events) and are released independently to their respective package registries.

## SDKs

| SDK | Language | Package | Registry |
|-----|----------|---------|----------|
| [sdks/node](sdks/node/) | TypeScript / Node.js 20+ | `@mobiscroll/connect-sdk` | [npm](https://www.npmjs.com/package/@mobiscroll/connect-sdk) |
| [sdks/python](sdks/python/) | Python 3.9+ | `mobiscroll-connect` | [PyPI](https://pypi.org/project/mobiscroll-connect/) |
| [sdks/php](sdks/php/) | PHP 8.1+ | `mobiscroll/connect-php` | [Packagist](https://packagist.org/packages/mobiscroll/connect-php) |
| [sdks/dotnet](sdks/dotnet/) | .NET 8 | `Mobiscroll.Connect` | [NuGet](https://www.nuget.org/packages/Mobiscroll.Connect) |
| [sdks/java](sdks/java/) | Java 11+ | `com.mobiscroll:connect-sdk` | [Maven Central](https://central.sonatype.com/artifact/com.mobiscroll/connect-sdk) |

Each SDK directory contains its own README, CLAUDE.md, and a `minimal-app/` (or `samples/MinimalApp/`) reference app you can run end-to-end.

## Repository layout

```
sdks/
├── node/      TypeScript SDK + minimal-app
├── python/    Python SDK + minimal Flask app
├── php/       PHP SDK + minimal-app
├── dotnet/    .NET SDK + samples/MinimalApp
└── java/      Java SDK + Spring Boot minimal-app
```

Each SDK is self-contained — `cd sdks/<lang>` and use that language's normal toolchain (`npm`, `pip`, `composer`, `dotnet`, `mvn`).

## Releases

Each SDK releases independently using path-scoped git tags:

| SDK | Tag prefix | Example |
|-----|-----------|---------|
| Node | `node-v*` | `node-v1.0.2` |
| Python | `python-v*` | `python-v0.2.0` |
| PHP | `php-v*` | `php-v1.0.1` |
| .NET | `dotnet-v*` | `dotnet-v1.0.2` |
| Java | `java-v*` | `java-v1.0.0` |

GitHub Actions workflows (`.github/workflows/release-*.yml`) publish to the corresponding registry when a matching tag is pushed.

## Documentation

Full API documentation lives at [mobiscroll.com/docs/connect](https://mobiscroll.com/docs/connect).

## License

[MIT](LICENSE)
