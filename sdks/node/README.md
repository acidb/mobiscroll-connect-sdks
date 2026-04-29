# @mobiscroll/connect-sdk

Node.js SDK for Mobiscroll Connect.

📖 **[Full documentation](https://mobiscroll.com/docs/connect/node-sdk)**

`@mobiscroll/connect-sdk` provides a typed client for:

- OAuth authorization and token exchange
- Listing connected calendars
- Listing, creating, updating, and deleting calendar events
- Working with multiple providers (Google, Microsoft, Apple, CalDAV)

## Installation

```bash
npm install @mobiscroll/connect-sdk
```

or

```bash
yarn add @mobiscroll/connect-sdk
```

## Requirements

- Node.js 20+

## Quick start

```ts
import { MobiscrollConnectClient } from '@mobiscroll/connect-sdk';

const client = new MobiscrollConnectClient({
	clientId: process.env.MOBISCROLL_CLIENT_ID!,
	clientSecret: process.env.MOBISCROLL_CLIENT_SECRET!,
	redirectUri: process.env.MOBISCROLL_REDIRECT_URI!,
});
```

## Authentication flow

### 1) Generate authorization URL

```ts
const url = client.auth.generateAuthUrl({
	userId: 'user-123',
	state: 'optional-state',
	scope: 'read-write',
	providers: 'google,microsoft,apple,caldav',
});

// Redirect the user to `url`
```

### 2) Exchange authorization code for tokens

```ts
const tokens = await client.auth.getToken(codeFromCallback);
client.setCredentials(tokens);
```

### 3) Listen for automatic token refresh updates

```ts
client.on('tokens', (updatedTokens) => {
	// Persist updated tokens in your storage
	console.log(updatedTokens);
});
```

## API usage

### List calendars

```ts
const calendars = await client.calendars.list();
```

### List events

```ts
const result = await client.events.list({
	start: new Date(),
	end: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000),
	pageSize: 50,
});

console.log(result.events);
```

### Create event

```ts
const created = await client.events.create({
	provider: 'google',
	calendarId: 'primary',
	title: 'Team sync',
	start: new Date('2026-03-20T09:00:00Z'),
	end: new Date('2026-03-20T09:30:00Z'),
});
```

### Update event

```ts
const updated = await client.events.update({
	provider: 'google',
	eventId: created.id,
	calendarId: created.calendarId,
	title: 'Team sync (updated)',
});
```

### Delete event

```ts
await client.events.delete({
	provider: 'google',
	calendarId: created.calendarId,
	eventId: created.id,
});
```

## Connection management

```ts
const status = await client.auth.getConnectionStatus();
console.log(status.connections);

await client.auth.disconnect({ provider: 'google', account: 'user@gmail.com' });
```

## Error handling

The SDK exposes typed errors:

- `AuthenticationError`
- `ValidationError`
- `NotFoundError`
- `RateLimitError`
- `ServerError`
- `NetworkError`
- `MobiscrollConnectError`

Example:

```ts
import { AuthenticationError, ValidationError } from '@mobiscroll/connect-sdk';

try {
	await client.events.list();
} catch (error) {
	if (error instanceof AuthenticationError) {
		// Handle auth failure
	} else if (error instanceof ValidationError) {
		// Handle invalid request data
	}
}
```

## Exports

The package exports:

- `MobiscrollConnectClient`
- `ApiClient`
- All public TypeScript types from `types.ts`

## Notes

The current client default base URL is:

`https://connect.mobiscroll.com/api`
