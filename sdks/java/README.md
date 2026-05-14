# Mobiscroll Connect — Java SDK

Official Java client for the [Mobiscroll Connect](https://mobiscroll.com/connect) API. One SDK over Google Calendar, Microsoft Outlook, Apple Calendar, and CalDAV.

- **Coordinates:** `com.mobiscroll:connect-sdk`
- **Min Java:** 11
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
