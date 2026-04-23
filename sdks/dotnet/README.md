# Mobiscroll Connect — .NET SDK

.NET SDK for the [Mobiscroll Connect](https://mobiscroll.com) API. Manage calendars and events across Google Calendar, Microsoft Outlook, Apple Calendar, and CalDAV through a single async client.

- Targets **.NET 8.0**.
- Ships with a sample ASP.NET Core minimal app under `samples/MinimalApp`.
- Parity with the [Node](https://github.com/mobiscroll/mobiscroll-connect-node) and [PHP](https://github.com/mobiscroll/mobiscroll-connect-php) SDKs.

## Install

```bash
dotnet add package Mobiscroll.Connect
```

## Quick start

```csharp
using Mobiscroll.Connect;
using Mobiscroll.Connect.Models;

var client = new MobiscrollConnectClient(
    clientId: "your-client-id",
    clientSecret: "your-client-secret",
    redirectUri: "https://your-app.example/callback");

client.OnTokensRefreshed(tokens => { /* persist tokens */ });

// 1. Redirect the user to the OAuth authorization URL
var authUrl = client.Auth.GenerateAuthUrl(new AuthorizeParams
{
    UserId = "user-123",
    Providers = "google,microsoft",
});

// 2. In your callback handler, exchange the code for tokens
var tokens = await client.Auth.GetTokenAsync(code);

// 3. Use the API
var calendars = await client.Calendars.ListAsync();
var events = await client.Events.ListAsync(new EventListParams
{
    PageSize = 50,
    Start = DateTime.UtcNow,
    End = DateTime.UtcNow.AddDays(30),
});
```

## ASP.NET Core / DI

```csharp
builder.Services.AddMobiscrollConnect(o =>
{
    o.ClientId = builder.Configuration["Mobiscroll:ClientId"]!;
    o.ClientSecret = builder.Configuration["Mobiscroll:ClientSecret"]!;
    o.RedirectUri = builder.Configuration["Mobiscroll:RedirectUri"]!;
});
```

## Error handling

All API errors derive from `MobiscrollConnectException`:

| Exception                  | Trigger                        |
| -------------------------- | ------------------------------ |
| `AuthenticationException`  | 401 / 403                      |
| `NotFoundException`        | 404                            |
| `ValidationException`      | 400 / 422 (`.Details`)         |
| `RateLimitException`       | 429 (`.RetryAfter`)            |
| `ServerException`          | 5xx (`.StatusCode`)            |
| `NetworkException`         | No response / timeout          |

## License

MIT
