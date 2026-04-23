# Mobiscroll Connect — .NET minimal sample

A single-file ASP.NET Core minimal API that demonstrates the full OAuth + list flow.

## Run (macOS, Linux, Windows)

1. Fill in your credentials in `appsettings.Development.json`:
   ```json
   "Mobiscroll": {
     "ClientId": "your-client-id",
     "ClientSecret": "your-client-secret",
     "RedirectUri": "http://localhost:5050/callback"
   }
   ```
   Register the same `RedirectUri` with your Mobiscroll Connect app.

2. Run:
   ```bash
   dotnet run
   ```

3. Open http://localhost:5050 → click "Connect a calendar account" → approve → you'll be redirected to `/calendars` with the JSON response.

4. Also try http://localhost:5050/events for a 30-day window of events.

## What this sample covers

- `AddMobiscrollConnect(...)` DI registration
- `GenerateAuthUrl` → browser redirect
- `GetTokenAsync(code)` in the callback
- `OnTokensRefreshed` callback (prints refreshed tokens to the in-memory store)
- `Calendars.ListAsync()` and `Events.ListAsync(params)`

Tokens are held in-process for the duration of the demo — replace with session/db storage for a real app.
