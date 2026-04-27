# Mobiscroll Connect .NET SDK

A .NET client library for the Mobiscroll Connect API, enabling seamless calendar and event management across multiple providers (Google Calendar, Microsoft Outlook, Apple Calendar, CalDAV).

📖 **[Full documentation](https://mobiscroll.com/docs/connect/dotnet-sdk)**

## Features

- **Multi-provider support**: Google Calendar, Microsoft Outlook, Apple Calendar, CalDAV
- **OAuth2 authentication**: Full authorization code flow with token exchange
- **Automatic token refresh**: Silently refreshes expired access tokens and retries the original request
- **Event management**: Create, read, update, and delete calendar events
- **Calendar operations**: List calendars from all connected providers
- **Connection management**: Check provider connection status and disconnect accounts
- **Typed exceptions**: Distinct error classes for authentication, validation, rate limiting, and more
- **ASP.NET Core integration**: First-class dependency injection support via `AddMobiscrollConnect()`
- **Async-first**: All API calls are `async`/`await` with `CancellationToken` support

## Requirements

- .NET 8 or higher

## Installation

```bash
dotnet add package Mobiscroll.Connect
```

## Setup

Create a Mobiscroll Connect application at the [Mobiscroll Connect dashboard](https://app.mobiscroll.com/connect) to obtain your **Client ID**, **Client Secret**, and configure your **Redirect URI**.

## Usage

### Initialize the Client

```csharp
using Mobiscroll.Connect;

var client = new MobiscrollConnectClient(
    clientId: "YOUR_CLIENT_ID",
    clientSecret: "YOUR_CLIENT_SECRET",
    redirectUri: "YOUR_REDIRECT_URI"
);
```

### ASP.NET Core / Dependency Injection

```csharp
// Program.cs
builder.Services.AddMobiscrollConnect(o =>
{
    o.ClientId     = builder.Configuration["Mobiscroll:ClientId"]!;
    o.ClientSecret = builder.Configuration["Mobiscroll:ClientSecret"]!;
    o.RedirectUri  = builder.Configuration["Mobiscroll:RedirectUri"]!;
});
```

Then inject `MobiscrollConnectClient` into controllers or minimal-API handlers as normal.

### Token Refresh

The SDK automatically refreshes expired access tokens. When a request returns `401 Unauthorized` and a refresh token is available, the SDK silently exchanges it for a new access token and retries the request.

Register a callback to persist the updated tokens — this is required so the new tokens survive future requests:

```csharp
client.OnTokensRefreshed(updatedTokens =>
{
    // Persist in your database or session store
    SaveTokensToDb(userId, updatedTokens);
});
```

If the refresh token is invalid or revoked, the SDK throws `AuthenticationException` and the user must re-authorize.

### OAuth2 Flow

#### Step 1: Generate the authorization URL

```csharp
string authUrl = client.Auth.GenerateAuthUrl(new AuthorizeParams
{
    UserId    = "your-app-user-id",
    // Optional:
    // Scope     = "read-write",
    // State     = "csrf-protection-value",
    // Providers = "google,microsoft",
});

// Redirect the user to authUrl
return Redirect(authUrl);
```

#### Step 2: Handle the callback and exchange the code for tokens

```csharp
string? code = Request.Query["code"];

TokenResponse tokenResponse = await client.Auth.GetTokenAsync(code);

// Persist all token fields — you need the RefreshToken for auto-refresh
SaveTokensToDb(userId, tokenResponse);
```

#### Step 3: Restore credentials and make API calls

```csharp
TokenResponse saved = LoadTokensFromDb(userId);
client.SetCredentials(saved);

// The client is now authenticated — make API calls
IReadOnlyList<Calendar> calendars = await client.Calendars.ListAsync();
```

### Calendars

```csharp
IReadOnlyList<Calendar> calendars = await client.Calendars.ListAsync();

foreach (Calendar cal in calendars)
    Console.WriteLine($"[{cal.Provider}] {cal.Title} ({cal.Id})");
```

### Events

#### List events

```csharp
EventsListResponse response = await client.Events.ListAsync(new EventListParams
{
    Start    = new DateTime(2024, 1, 1, 0, 0, 0, DateTimeKind.Utc),
    End      = new DateTime(2024, 1, 31, 23, 59, 59, DateTimeKind.Utc),
    CalendarIds = new Dictionary<string, List<string>>
    {
        ["google"] = new() { "primary" },
    },
    PageSize = 50,
});

foreach (CalendarEvent e in response.Events)
    Console.WriteLine($"{e.Title}: {e.Start:g} – {e.End:g}");

// Load the next page
if (response.NextPageToken is not null)
{
    var next = await client.Events.ListAsync(new EventListParams
    {
        PageSize      = 50,
        NextPageToken = response.NextPageToken,
    });
}
```

#### Create an event

```csharp
CalendarEvent created = await client.Events.CreateAsync(new EventCreateData
{
    Provider    = Provider.Google,
    CalendarId  = "primary",
    Title       = "Team Meeting",
    Start       = new DateTime(2024, 6, 15, 10, 0, 0, DateTimeKind.Utc),
    End         = new DateTime(2024, 6, 15, 11, 0, 0, DateTimeKind.Utc),
    Description = "Quarterly review",
    Location    = "Conference Room A",
});

Console.WriteLine($"Created: {created.Id}");
```

#### Update an event

```csharp
CalendarEvent updated = await client.Events.UpdateAsync(new EventUpdateData
{
    Provider   = Provider.Google,
    CalendarId = "primary",
    EventId    = "event-id-to-update",
    Title      = "Team Meeting (Rescheduled)",
    Start      = new DateTime(2024, 6, 15, 14, 0, 0, DateTimeKind.Utc),
    End        = new DateTime(2024, 6, 15, 15, 0, 0, DateTimeKind.Utc),
});
```

#### Delete an event

```csharp
await client.Events.DeleteAsync(new EventDeleteParams
{
    Provider   = Provider.Google,
    CalendarId = "primary",
    EventId    = "event-id-to-delete",
});
```

#### Recurring events

```csharp
// Update only this instance of a recurring event
await client.Events.UpdateAsync(new EventUpdateData
{
    Provider         = Provider.Google,
    CalendarId       = "primary",
    EventId          = "instance-id",
    RecurringEventId = "series-id",
    UpdateMode       = "this",
    Title            = "One-off title change",
});

// Delete this and all following instances
await client.Events.DeleteAsync(new EventDeleteParams
{
    Provider         = Provider.Google,
    CalendarId       = "primary",
    EventId          = "instance-id",
    RecurringEventId = "series-id",
    DeleteMode       = "following",
});
```

### Connection Management

```csharp
// Check which providers are connected
ConnectionStatusResponse status = await client.Auth.GetConnectionStatusAsync();

foreach (ConnectedAccount acct in status.Connections.Google)
    Console.WriteLine($"Google: {acct.Display} ({acct.Id})");

if (status.LimitReached)
    Console.WriteLine($"Connection limit of {status.Limit} reached");

// Disconnect a provider
DisconnectResponse result = await client.Auth.DisconnectAsync(new DisconnectParams
{
    Provider = Provider.Google,
});

if (result.Success)
    Console.WriteLine("Disconnected successfully");
```

## Error Handling

All SDK methods throw exceptions that extend `MobiscrollConnectException`:

| Exception                 | HTTP Status | Extra member          |
|---|---|---|
| `AuthenticationException` | 401, 403    | —                     |
| `ValidationException`     | 400, 422    | `Details` (`object?`) |
| `NotFoundException`       | 404         | —                     |
| `RateLimitException`      | 429         | `RetryAfter` (`int?`) |
| `ServerException`         | 5xx         | `StatusCode` (`int`)  |
| `NetworkException`        | —           | —                     |

```csharp
using Mobiscroll.Connect.Exceptions;

try
{
    var events = await client.Events.ListAsync();
}
catch (AuthenticationException)
{
    // Token expired and refresh failed — re-authorize the user
}
catch (ValidationException ex)
{
    var details = ex.Details; // field-level errors
}
catch (NotFoundException)
{
    // Calendar or event not found
}
catch (RateLimitException ex)
{
    var retryAfter = ex.RetryAfter; // seconds
}
catch (ServerException ex)
{
    var status = ex.StatusCode; // 500, 502, 503, 504
}
catch (NetworkException)
{
    // Connection failed
}
catch (MobiscrollConnectException)
{
    // Catch-all
}
```

## Testing

```bash
# Build the solution
dotnet build

# Run all tests
dotnet test

# Run with coverage
dotnet test --collect:"XPlat Code Coverage"
```

## Project Structure

```
src/
└── Mobiscroll.Connect/
    ├── Exceptions/
    │   ├── MobiscrollConnectException.cs
    │   ├── AuthenticationException.cs
    │   ├── ValidationException.cs
    │   ├── NotFoundException.cs
    │   ├── RateLimitException.cs
    │   ├── ServerException.cs
    │   └── NetworkException.cs
    ├── Models/
    │   ├── TokenResponse.cs
    │   ├── CalendarEvent.cs
    │   ├── EventsListResponse.cs
    │   ├── Calendar.cs
    │   ├── ConnectionStatusResponse.cs
    │   ├── DisconnectResponse.cs
    │   ├── AuthorizeParams.cs
    │   ├── EventListParams.cs
    │   ├── EventCreateData.cs
    │   ├── EventUpdateData.cs
    │   ├── EventDeleteParams.cs
    │   ├── DisconnectParams.cs
    │   ├── EventAttendee.cs
    │   └── RecurrenceRule.cs
    ├── Resources/
    │   ├── Auth.cs
    │   ├── Calendars.cs
    │   └── Events.cs
    ├── ApiClient.cs
    ├── MobiscrollConnectClient.cs
    ├── MobiscrollConnectConfig.cs
    └── Provider.cs
tests/
└── Mobiscroll.Connect.Tests/
    ├── ApiClientTests.cs
    ├── AuthTests.cs
    ├── CalendarsTests.cs
    ├── EventsTests.cs
    ├── ErrorMappingTests.cs
    └── SerializationTests.cs
samples/
└── MinimalApp/
    └── Program.cs
```

## License

[MIT](https://github.com/acidb/mobiscroll-connect-dotnet/tree/main?tab=MIT-1-ov-file#readme)
