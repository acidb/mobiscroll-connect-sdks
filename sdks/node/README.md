# @mobiscroll/connect-sdk

Node.js client for [Mobiscroll Connect](https://mobiscroll.com/connect), the calendar connectivity layer for scheduling products — Google Calendar, Microsoft Outlook, Apple Calendar and CalDAV through one API. Backend only — works with your own UI.

[![npm](https://img.shields.io/npm/v/@mobiscroll/connect-sdk?label=npm)](https://www.npmjs.com/package/@mobiscroll/connect-sdk)

**[npm](https://www.npmjs.com/package/@mobiscroll/connect-sdk)** · **[Documentation](https://mobiscroll.com/docs/connect/node-sdk)** · **[Changelog](https://github.com/acidb/mobiscroll-connect-sdks/blob/main/sdks/node/CHANGELOG.md)** · **[Source](https://github.com/acidb/mobiscroll-connect-sdks/tree/main/sdks/node)**

`@mobiscroll/connect-sdk` provides a typed client for:

- OAuth authorization and token exchange
- Listing connected calendars
- Listing, creating, updating, and deleting calendar events
- Subscribing to and unsubscribing from calendar webhook notifications, and verifying their signatures
- Working with Google Calendar, Microsoft Outlook, Apple Calendar and CalDAV

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
	lng: 'es', // optional: Connect page language, see https://mobiscroll.com/docs/connect/localization#supported-languages
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

**Running more than one instance.** Concurrent calls on the same client that hit an expired token share one in-flight refresh. The SDK does not coordinate across processes — separate containers, cluster workers or serverless invocations each refresh from the tokens they hold in memory. Connect accepts concurrent refreshes of the same token, but refreshing with a copy that is two or more refreshes out of date revokes the user's authorization. Persist refreshed tokens to storage every instance reads, and call `client.setCredentials(tokens)` with the current tokens when a process starts a job or handles a request. See [Refreshing from several instances](https://mobiscroll.com/docs/connect/api/oauth#concurrent-refresh).

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

### Subscribe to webhook notifications

```ts
const subscription = await client.webhooks.subscribeWebhook({
	provider: 'google',
	calendarId: 'primary',
});

console.log(subscription.channelId);
```

### Unsubscribe from webhook notifications

```ts
await client.webhooks.unsubscribeWebhook({
	provider: 'google',
	channelId: subscription.channelId,
	resourceId: subscription.subscription.resourceId,
});
```

### Verify webhook deliveries

Every delivery to your webhook URL is signed. `verifyWebhook()` checks the signature and the timestamp, then returns the parsed delivery, or throws `WebhookVerificationError`. Pass the **raw** request body: a JSON body parser that runs first changes the bytes and every check fails.

```ts
import express from 'express';
import { WebhookVerificationError } from '@mobiscroll/connect-sdk';

app.post('/webhooks/mobiscroll', express.raw({ type: 'application/json' }), async (req, res) => {
	let delivery;
	try {
		delivery = await client.webhooks.verifyWebhook(req.body, req.headers);
	} catch (error) {
		if (error instanceof WebhookVerificationError) {
			// 503 lets Connect retry when the keys could not be loaded; 401 is final.
			return res.sendStatus(error.reason === 'no_public_keys' ? 503 : 401);
		}
		throw error;
	}
	res.sendStatus(204);
	handleDelivery(delivery);
});
```

The public keys are fetched from `https://connect.mobiscroll.com/.well-known/webhook-keys` on the first delivery and cached for the whole process as the endpoint's `Cache-Control` allows. If no signature matches, the keys are fetched again (at most once a minute) before the delivery is rejected, so key rotations need no action on your side.

If your handler cannot make outbound requests, pin the key. `webhookPublicKey` is used only when the keys endpoint cannot be reached; it stops working when Mobiscroll retires that key, so you must replace it on every rotation.

```ts
const client = new MobiscrollConnectClient({
	clientId: process.env.MOBISCROLL_CLIENT_ID!,
	clientSecret: process.env.MOBISCROLL_CLIENT_SECRET!,
	redirectUri: process.env.MOBISCROLL_REDIRECT_URI!,
	webhookPublicKey: process.env.MOBISCROLL_WEBHOOK_PUBLIC_KEY, // whpk_...
});
```

To check against keys you supply, with no fetching, call `verifyWebhookSignature(rawBody, headers, ['whpk_...'])`. It throws `WebhookVerificationError` and returns nothing. See [Verifying deliveries](https://mobiscroll.com/docs/connect/api/webhooks#verifying-deliveries).

## Connection management

```ts
const status = await client.auth.getConnectionStatus();
console.log(status.connections);

// An account can connect without granting calendar access — Google's consent screen
// lets the user untick that permission. Such accounts list no calendars until the
// user reconnects and allows it.
const needsCalendarAccess = status.connections.google.filter((account) => account.calendarPermissionGranted === false);

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
- `WebhookVerificationError` (from `verifyWebhook()`; its `reason` says why)
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
- `verifyWebhookSignature`
- All public TypeScript types from `types.ts`

## Notes

The current client default base URL is:

`https://connect.mobiscroll.com/api`
