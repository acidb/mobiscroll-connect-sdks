# Mobiscroll Connect — Java SDK

Java client for [Mobiscroll Connect](https://mobiscroll.com/connect), the calendar connectivity layer for scheduling products — Google Calendar, Microsoft Outlook, Apple Calendar and CalDAV through one API. Backend only — works with your own UI.

[![Maven Central](https://img.shields.io/maven-central/v/com.mobiscroll/connect-sdk?label=Maven%20Central)](https://central.sonatype.com/artifact/com.mobiscroll/connect-sdk)

**[Maven Central](https://central.sonatype.com/artifact/com.mobiscroll/connect-sdk)** · **[Documentation](https://mobiscroll.com/docs/connect/java-sdk)** · **[Changelog](https://github.com/acidb/mobiscroll-connect-sdks/blob/main/sdks/java/CHANGELOG.md)** · **[Source](https://github.com/acidb/mobiscroll-connect-sdks/tree/main/sdks/java)**

- **Coordinates:** `com.mobiscroll:connect-sdk`
- **Min Java:** 17
- **HTTP:** OkHttp 4
- **JSON:** Jackson

## Install

**Maven**

```xml
<dependency>
  <groupId>com.mobiscroll</groupId>
  <artifactId>connect-sdk</artifactId>
  <version>1.0.0</version>
</dependency>
```

**Gradle**

```kotlin
implementation("com.mobiscroll:connect-sdk:1.0.0")
```

## Quick start

```java
import com.mobiscroll.connect.MobiscrollConnectClient;
import com.mobiscroll.connect.Provider;
import com.mobiscroll.connect.models.AuthUrlParams;
import com.mobiscroll.connect.models.Calendar;
import com.mobiscroll.connect.models.EventCreateData;
import com.mobiscroll.connect.models.TokenResponse;
import java.time.OffsetDateTime;
import java.util.List;

MobiscrollConnectClient client = new MobiscrollConnectClient(
    System.getenv("MOBISCROLL_CLIENT_ID"),
    System.getenv("MOBISCROLL_CLIENT_SECRET"),
    "https://your-app.example/oauth/callback");

// 1. Send the user to the consent URL.
String authUrl = client.auth().generateAuthUrl(AuthUrlParams.builder()
    .userId("user-123")
    .providers(List.of(Provider.GOOGLE))
    .lng("es") // optional: Connect page language, see https://mobiscroll.com/docs/connect/localization#supported-languages
    .build());

// 2. On the callback, exchange the code.
TokenResponse tokens = client.auth().getToken(callbackCode);
// (persist `tokens` server-side keyed by your user)

// 3. Use the resources.
List<Calendar> calendars = client.calendars().list();
client.events().create(EventCreateData.builder()
    .provider(Provider.GOOGLE)
    .calendarId(calendars.get(0).getId())
    .title("Standup")
    .start(OffsetDateTime.parse("2026-06-01T09:00:00Z"))
    .end(OffsetDateTime.parse("2026-06-01T09:30:00Z"))
    .build());
```

## Webhooks

Subscribe to change notifications for a calendar, and unsubscribe when you're done:

```java
import com.mobiscroll.connect.models.WebhookSubscribeParams;
import com.mobiscroll.connect.models.WebhookSubscribeResponse;
import com.mobiscroll.connect.models.WebhookUnsubscribeParams;

WebhookSubscribeResponse subscription = client.webhooks().subscribeWebhook(WebhookSubscribeParams.builder()
    .provider(Provider.GOOGLE)
    .calendarId(calendars.get(0).getId())
    .build());
// persist subscription.getChannelId() / subscription.getSubscription().getResourceId()
// so you can unsubscribe later.

client.webhooks().unsubscribeWebhook(WebhookUnsubscribeParams.builder()
    .provider(Provider.GOOGLE)
    .channelId(subscription.getChannelId())
    .resourceId(subscription.getSubscription().getResourceId())
    .build());
```

### Verify webhook deliveries

Every delivery to your webhook URL is signed. `verifyWebhook()` checks the signature and the timestamp, then returns the parsed `WebhookDelivery`, or throws `WebhookVerificationException`. Pass the **raw** request body: binding the body to a parsed object first changes the bytes and every check fails. Header names are matched case-insensitively; both `Map<String, String>` and `Map<String, List<String>>` work.

```java
import com.mobiscroll.connect.exceptions.WebhookVerificationException;
import com.mobiscroll.connect.models.WebhookDelivery;

@PostMapping("/webhooks/mobiscroll")
ResponseEntity<Void> webhook(@RequestBody byte[] body, @RequestHeader Map<String, String> headers) {
    WebhookDelivery delivery;
    try {
        delivery = client.webhooks().verifyWebhook(body, headers);
    } catch (WebhookVerificationException e) {
        // 503 lets Connect retry when the keys could not be loaded; 401 is final.
        boolean noKeys = e.getReason() == WebhookVerificationException.Reason.NO_PUBLIC_KEYS;
        return ResponseEntity.status(noKeys ? 503 : 401).build();
    }
    handleDelivery(delivery);
    return ResponseEntity.noContent().build();
}
```

The public keys are fetched from `https://connect.mobiscroll.com/.well-known/webhook-keys` on the first delivery and cached for the whole process (shared by every client instance) as the endpoint's `Cache-Control` allows. If no signature matches, the keys are fetched again (at most once a minute) before the delivery is rejected, so key rotations need no action on your side. `verifyWebhook()` blocks while the keys are fetched.

If your handler cannot make outbound requests, pin the key. `webhookPublicKey` is used only when the keys endpoint cannot be reached; it stops working when Mobiscroll retires that key, so you must replace it on every rotation.

```java
MobiscrollConnectClient client = new MobiscrollConnectClient(
    MobiscrollConnectConfig.builder()
        .clientId(clientId)
        .clientSecret(clientSecret)
        .redirectUri(redirectUri)
        .webhookPublicKey(System.getenv("MOBISCROLL_WEBHOOK_PUBLIC_KEY")) // whpk_...
        .build());
```

To check against keys you supply, with no fetching, call `WebhookVerifier.verifyWebhookSignature(rawBody, headers, List.of("whpk_..."))`. It throws `WebhookVerificationException` and returns nothing. See [Verifying deliveries](https://mobiscroll.com/docs/connect/api/webhooks#verifying-deliveries).

## Configuration

For a custom base URL, HTTP timeout, OkHttp client, or a token-refresh callback for persistence, use the builder:

```java
MobiscrollConnectClient client = new MobiscrollConnectClient(
    MobiscrollConnectConfig.builder()
        .clientId(clientId)
        .clientSecret(clientSecret)
        .redirectUri(redirectUri)
        .baseUrl("https://connect.mobiscroll.com/api")
        .timeout(Duration.ofSeconds(60))
        .onTokensRefreshed(newTokens -> {
            // Persist newTokens.getAccessToken() / getRefreshToken() to your store.
        })
        .build());
```

## Token refresh

When a request returns `401 Unauthorized` and a refresh token is available, the SDK exchanges it for a new access token and retries the request. Persist the new tokens in the `onTokensRefreshed` callback shown above. If the refresh itself fails, the SDK throws `AuthenticationException` and the user must re-authorize.

**Running more than one instance.** Concurrent calls on the same client share a single refresh attempt. The SDK does not coordinate across processes — separate containers, cluster workers or serverless invocations each refresh from the tokens they hold in memory. Connect accepts concurrent refreshes of the same token, but refreshing with a copy that is two or more refreshes out of date revokes the user's authorization. Persist refreshed tokens to storage every instance reads, and call `client.setCredentials(tokens)` with the current tokens when a process starts a job or handles a request. See [Refreshing from several instances](https://mobiscroll.com/docs/connect/api/oauth#concurrent-refresh).

## Error handling

The SDK throws typed exceptions, all extending `com.mobiscroll.connect.exceptions.MobiscrollConnectException`:

| HTTP | Exception | Extra |
|---|---|---|
| 401 / 403 | `AuthenticationException` | — (after refresh+retry has been exhausted) |
| 404 | `NotFoundException` | — |
| 400 / 422 | `ValidationException` | `getDetails()` (`JsonNode`) |
| 429 | `RateLimitException` | `getRetryAfter()` (`Integer`, seconds) |
| 5xx | `ServerException` | `getStatusCode()` |
| Transport (timeout / DNS / reset) | `NetworkException` | wraps cause |
| Webhook delivery fails `verifyWebhook()` | `WebhookVerificationException` | `getReason()` |

## Minimal app

A runnable Spring Boot demo lives under [`minimal-app/`](minimal-app/):

```bash
cd minimal-app
MOBISCROLL_CLIENT_ID=... MOBISCROLL_CLIENT_SECRET=... \
MOBISCROLL_REDIRECT_URI=http://localhost:8080/oauth/callback \
mvn spring-boot:run
```

Then open <http://localhost:8080>.

## License

[MIT](../../LICENSE)
