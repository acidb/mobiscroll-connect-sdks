# CLAUDE.md — Mobiscroll Connect SDKs (Monorepo)

This is a polyglot monorepo containing four official client SDKs for the Mobiscroll Connect API: Node.js, Python, PHP, and .NET. Each SDK is self-contained under `sdks/<lang>/` with its own toolchain, tests, and CLAUDE.md. This file covers cross-SDK invariants and monorepo workflow.

When you work on a specific SDK, also read its per-SDK [sdks/node/CLAUDE.md](sdks/node/CLAUDE.md) / [sdks/python/CLAUDE.md](sdks/python/CLAUDE.md) / [sdks/php/CLAUDE.md](sdks/php/CLAUDE.md) / [sdks/dotnet/CLAUDE.md](sdks/dotnet/CLAUDE.md) — those define the language-specific style and architecture that supersedes anything here.

## Repository layout

```
sdks/
├── node/      TypeScript / Node 20+      → npm: @mobiscroll/connect-sdk
├── python/    Python 3.9+ (httpx)         → PyPI: mobiscroll-connect-sdk
├── php/       PHP 8.1+ (Guzzle 7)         → Packagist: mobiscroll/connect-php
└── dotnet/    .NET 8 (HttpClient)         → NuGet: Mobiscroll.Connect

.github/workflows/   path-filtered CI per SDK + (later) tag-driven release
scripts/             release.sh, bump-version.sh
```

Each `sdks/<lang>/` is independently buildable: `cd sdks/python && pip install -e ".[dev]" && pytest` works exactly like in a single-SDK repo. The monorepo adds zero language-level coupling.

## Cross-SDK invariants

All four SDKs implement the **same public surface** against the same backend. When changing one, consider whether the other three need the parallel change.

**Resources** (each SDK exposes these as a top-level client property):
- `Auth` — OAuth authorization, token exchange, refresh
- `Calendars` — list, get, sync calendars across providers
- `Events` — CRUD + list events on a calendar

**Provider coverage** (identical across all four): Google Calendar, Microsoft Outlook (Graph), Apple Calendar, CalDAV.

**Error taxonomy** (mapped to language-idiomatic exception types in each SDK, but the categories are the same):
- Authentication errors (401, expired/invalid token)
- Authorization errors (403, missing scope)
- Validation errors (400, malformed request)
- Not-found errors (404)
- Rate-limit errors (429, with retry-after)
- Provider errors (502/503 from upstream calendar provider)
- Network/transport errors

**Token-refresh semantics:** all SDKs auto-refresh expired access tokens once per request, retry the original call once, then surface the auth error if the refresh also fails. Do not change this behavior in only one SDK.

**Naming parity:** the same operation has the same name across SDKs, adjusted for language case conventions. `listCalendars` in Node, `list_calendars` in Python, `listCalendars` in PHP, `ListCalendarsAsync` in .NET. When adding an operation, name it the same in all four.

## Cross-SDK changes — workflow

1. Make the change in one SDK first (whichever you're most comfortable with), get the tests passing.
2. Replicate the change in the other three. Per-SDK CLAUDE.md files dictate idiomatic style — don't fight them.
3. Update each SDK's README/CHANGELOG separately if user-visible.
4. Single PR is preferred for a synchronized cross-SDK change. The path-filtered CI runs all four jobs in parallel.

## Releases

**Releases are independent per SDK.** Each SDK has its own version cadence. Don't force lockstep versions just because the change is cross-SDK.

**Tag prefixes:**

| SDK | Tag prefix | Example |
|-----|-----------|---------|
| Node | `node-v*` | `node-v1.0.2` |
| Python | `python-v*` | `python-v0.2.0` |
| PHP | `php-v*` | `php-v1.0.1` |
| .NET | `dotnet-v*` | `dotnet-v1.0.2` |

**To release:**
```bash
scripts/release.sh <sdk> <version>   # bumps version files, commits, tags, pushes
```

The matching `.github/workflows/release-<sdk>.yml` workflow (added once the GitHub repo is set up) picks up the tag and publishes to the registry.

**Where versions live** (the bump script handles all of these):
- Node: `sdks/node/package.json` → `version`
- Python: **two places** — `sdks/python/pyproject.toml` (`version = "X"`) AND `sdks/python/mobiscroll_connect/__init__.py` (`__version__ = "X"`). Keep them in sync.
- PHP: tag-driven only (Packagist resolves from git tags; no file change)
- .NET: `sdks/dotnet/src/Mobiscroll.Connect/Mobiscroll.Connect.csproj` → `<Version>`

**PHP-specific gotcha:** Packagist reads the `composer.json` at the **root** of the registered repo. Since the monorepo's PHP `composer.json` lives at `sdks/php/composer.json`, the release workflow uses `git subtree split` to push `sdks/php/` to a thin mirror repo (the existing [acidb/mobiscroll-connect-php](https://github.com/acidb/mobiscroll-connect-php) repo, repurposed as a publish-only mirror). Developers only ever edit in this monorepo; never edit the mirror directly.

## Per-SDK quick reference

| Task | Node | Python | PHP | .NET |
|------|------|--------|-----|------|
| Install deps | `npm ci` | `pip install -e ".[dev]"` | `composer install` | `dotnet restore` |
| Run tests | `npm test` | `pytest` | `composer run test` | `dotnet test` |
| Lint | `npm run lint` | `ruff check .` | `composer run lint` | (warnings-as-errors via build) |
| Type check | `tsc --noEmit` | `mypy mobiscroll_connect` | `composer run stan` | (compiler) |
| Build | `npm run build` | `python -m build` | (n/a) | `dotnet build` |
| Smoke test | (README examples) | `cd minimal-app && python app.py` | `cd minimal-app && php -S ...` | `cd samples/MinimalApp && dotnet run` |

All commands assume `cwd = sdks/<lang>/`.

## What goes where

- **Cross-SDK convention or invariant** → this file
- **Language-specific style, architecture, idioms** → `sdks/<lang>/CLAUDE.md`
- **Release process specific to one SDK** → `sdks/<lang>/RELEASE_CHECKLIST.md` if it exists, else `sdks/<lang>/CLAUDE.md`
- **Workflow/scripts that touch multiple SDKs** → `scripts/`
