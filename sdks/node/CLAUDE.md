# CLAUDE.md — Mobiscroll Connect Node.js SDK

## Personal preferences

I am a Medior Full Stack Engineer at Mobiscroll working on the "Connect" product. I am an experienced developer, so do not explain basic programming concepts; focus on architectural patterns, edge cases, and optimization. My work involves UI components, data synchronization, and integration features, where performance and a smooth user experience are critical.
Communication Style:

- Be extremely concise. Skip introductory and concluding fluff (e.g., "Here is the code," "I'd be happy to help"). Get straight to the answer.
- If a question is about code, provide the code block first, then explain the logic briefly afterward.
- Never hallucinate. If you don't know something or a library version is too new, tell me immediately.
  Coding Standards:
- Default to TypeScript for all frontend and backend examples using modern ES6+ syntax unless specified otherwise.
- Favor modular, reusable code. Prioritize component lifecycle efficiency and minimizing re-renders.
- Always consider WCAG compliance and touch-responsiveness in all UI-related code.
- Include proper error handling and edge cases; do not just provide the "happy path."
- Keep logic decoupled from framework-specific hooks where possible, or provide clean "vanilla" logic explanations if the context allows.
  Formatting Guidelines:
- When modifying existing code, provide the entire function or component for easy copy-pasting. Never truncate code with comments like // ... rest of code unless the file is excessively long.
- Use JSDoc for complex functions to explain parameters and return types.

## Project Overview

TypeScript SDK for the Mobiscroll Connect API. Published to npm as `@mobiscroll/connect-sdk`. Enables calendar and event management across Google Calendar, Microsoft Outlook, Apple Calendar, and CalDAV through a single async client.

- **npm package**: `@mobiscroll/connect-sdk`
- **Entry point**: `src/index.ts` → compiled to `dist/index.js` + `dist/index.d.ts`
- **HTTP client**: Axios 1.x
- **Runtime**: Node.js 20+
- **Language**: TypeScript 5.x, strict mode
- **Test framework**: Jest 30 + ts-jest

---

## Architecture

```
MobiscrollConnectClient        — public entry point; constructs ApiClient + resources
  ├── auth: Auth               — generateAuthUrl, getToken, setCredentials, getConnectionStatus, disconnect
  ├── calendars: Calendars     — list()
  └── events: Events           — list(), create(), update(), delete()

ApiClient (extends EventEmitter)
  — Axios instance with base URL https://connect.mobiscroll.com/api
  — Request interceptor: injects Authorization: Bearer header
  — Response interceptor: handles 401 → token refresh → retry (with dedup via refreshTokenPromise)
  — Emits 'tokens' event after successful refresh with updated TokenResponse
  — handleError(): maps Axios errors to typed SDK error classes

types.ts                       — all exported interfaces, types, enums, and error classes
```

### Token refresh

`ApiClient` uses an Axios response interceptor. On 401, if a refresh token is stored and the request has not already been retried (`_retry` flag), it calls `refreshAccessToken()`. To prevent concurrent refresh races, a single `refreshTokenPromise` is shared — parallel 401s all await the same promise. After refresh, the `'tokens'` event is emitted so callers can persist the new credentials. The retry is executed by replaying the original request config.

### Error classes

All errors extend `MobiscrollConnectError` (which extends `Error`). Defined in `src/types.ts`:

| Class                 | Code                   | Extra field           |
| --------------------- | ---------------------- | --------------------- |
| `AuthenticationError` | `AUTHENTICATION_ERROR` | —                     |
| `NotFoundError`       | `NOT_FOUND_ERROR`      | —                     |
| `ValidationError`     | `VALIDATION_ERROR`     | `details?: unknown`   |
| `RateLimitError`      | `RATE_LIMIT_ERROR`     | `retryAfter?: number` |
| `ServerError`         | `SERVER_ERROR`         | `status: number`      |
| `NetworkError`        | `NETWORK_ERROR`        | —                     |

---

## Essential Commands

```bash
npm install               # install dependencies
npm run build             # tsc → dist/
npm run build:watch       # tsc --watch
npm test                  # jest
npm run test:watch        # jest --watch
npm run test:coverage     # jest --coverage
npm run lint              # eslint src/**/*.ts
npm run lint:fix          # eslint --fix
npm run format            # prettier --write
npm run prerelease        # bash pre-release.sh (version bump + build)
```

---

## File Map

| File                         | Purpose                                                                                         |
| ---------------------------- | ----------------------------------------------------------------------------------------------- |
| `src/index.ts`               | `MobiscrollConnectClient` class + re-exports from `./types` and `./client`                      |
| `src/client.ts`              | `ApiClient` — Axios setup, interceptors, token refresh, HTTP methods                            |
| `src/types.ts`               | All types, interfaces, enums, and error classes                                                 |
| `src/resources/auth.ts`      | `Auth` — OAuth flow: generateAuthUrl, getToken, setCredentials, getConnectionStatus, disconnect |
| `src/resources/calendars.ts` | `Calendars` — list()                                                                            |
| `src/resources/events.ts`    | `Events` — list(), create(), update(), delete()                                                 |
| `src/__tests__/`             | Jest tests per resource + client + types                                                        |
| `dist/`                      | Compiled output (do not edit directly)                                                          |

---

## Coding Standards

- TypeScript strict mode throughout; no `any` except in Axios internals where unavoidable.
- All public methods are `async` and return typed `Promise<T>`.
- JSDoc block comments (`/** ... */`) on public methods: `@param`, `@returns`, `@example` where the call pattern is non-obvious. Do not add comments that restate what the code does.
- No inline comments except where logic is genuinely non-obvious (e.g. `_retry` flag purpose, `refreshTokenPromise` dedup pattern).
- Interfaces for request/response shapes; `type` aliases for unions and mapped types.
- Error classes use `this.name = 'ClassName'` assignment in constructor so `instanceof` works across module boundaries.
- `ProviderEnum` for the four provider string values — prefer the enum over raw strings in implementation code.
- `EventCreateData`, `EventUpdateData` (`extends Partial<EventCreateData>`), `EventDeleteData` are the canonical input types; `EventResponse` (`= CalendarEvent`) is the canonical output type.

---

## Testing Patterns

- Mock `ApiClient` methods (`get`, `post`, `put`, `delete`) with `jest.fn()`.
- Do not test Axios internals directly — test behaviour through the resource classes.
- Token refresh behaviour is tested via the interceptor: simulate a 401 response, verify the retry fires and the `'tokens'` event is emitted.
- Type correctness tests in `types.test.ts` verify enum values and interface shapes at compile time.

---

## Key Invariants

- `refreshTokenPromise` is set to `null` in the `.finally()` handler — concurrent refresh calls share one in-flight promise.
- Token merge on refresh: `{ ...existing, ...newTokens, refresh_token: newTokens.refresh_token || existing.refresh_token }`.
- `getToken` in `Auth` calls `this.setCredentials(response.data)` immediately after exchange — credentials are always set before returning.
- `generateAuthUrl` builds the URL from `this.client.baseURL` (Axios default), not a hardcoded string — changing `baseURL` on the Axios instance propagates correctly.
- `CLIENT_ID` header is sent alongside `Authorization: Basic` on token exchange requests (`getToken` and `refreshAccessToken`).
- The `on()` method on `MobiscrollConnectClient` delegates to `ApiClient` (which extends `EventEmitter`) and returns `this` for chaining.
- `dist/` is the published output. `src/` is the source of truth. Never edit `dist/` directly.
