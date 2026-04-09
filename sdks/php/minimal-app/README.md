# Minimal SDK Test App

This is a tiny PHP app for testing `mobiscroll/connect-php` locally.

## Requirements

You need **real Mobiscroll OAuth credentials**:
- `MOBISCROLL_CLIENT_ID` - OAuth app client ID
- `MOBISCROLL_CLIENT_SECRET` - OAuth app client secret
- Valid redirect URI registered in your OAuth app settings

## Setup

### 1) Install dependencies

```bash
cd /Users/bence.kovacs/Repos/mobiscroll-connect-php/minimal-app
composer install
```

### 2) Create `.env` file

Copy `.env.example` and fill in real credentials:

```bash
cp .env.example .env
# Edit .env with your real Mobiscroll OAuth credentials
```

Or set env vars directly:

```bash
export MOBISCROLL_CLIENT_ID="your-real-client-id"
export MOBISCROLL_CLIENT_SECRET="your-real-client-secret"
export MOBISCROLL_REDIRECT_URI="http://localhost:8080"
export MOBISCROLL_USER_ID="your-test-user-id"
export MOBISCROLL_PROVIDER="google"  # or microsoft, apple, caldav
```

### 3) Start local server

```bash
php -S localhost:8080 -t public
```

## OAuth Flow

### Optional: Use the Built-in Frontend Test UI

Open:

```
http://localhost:8080/?action=ui
```

From this page you can:
- Generate the OAuth URL
- Open the provider consent page in a new tab
- Check session token status
- List calendars and events
- Clear stored session token

Recommended flow in the UI:
1. Click `Generate Auth URL`
2. Click `Open OAuth Page`
3. Complete consent in the new tab
4. Back in UI, click `Check Session`
5. Click `List Calendars` or `List Events`

### Step 1: Get Auth URL

```
http://localhost:8080/?action=auth-url
```

Response:
```json
{
  "ok": true,
  "authUrl": "https://connect.mobiscroll.com/oauth/authorize?...",
  "redirectUri": "http://localhost:8080"
}
```

### Step 2: Open Auth URL in Browser

Click the `authUrl` link and complete OAuth flow with your calendar provider (Google, Microsoft, Apple, etc).

After you authorize, you'll be redirected to:
```
http://localhost:8080/?action=callback&code=AUTHORIZATION_CODE
```

The app will exchange the code for a token and store it in the session.

### Step 3: Call Protected Endpoints

Now you can access calendars and events:

```
http://localhost:8080/?action=calendars
http://localhost:8080/?action=events
```

## Endpoints

| Endpoint | Auth | Purpose |
|----------|------|---------|
| `?action=auth-url` | ❌ | Generate OAuth authorization URL |
| `?action=callback&code=...` | ✅ | OAuth callback handler (auto-called by browser redirect) |
| `?action=calendars` | ✅ | List all calendars from connected providers |
| `?action=events` | ✅ | List recent events |
| `?action=session` | ❌ | Show stored session state (for debugging) |

## Troubleshooting

**"The requested resource does not exist or is not accessible"**
- The backend endpoints require a valid Bearer token
- Make sure you've completed the OAuth flow: visit `?action=auth-url`, authorize, let it redirect back to `/callback`
- Check `?action=session` to confirm token is stored

**"Missing env vars"**
- Make sure credentials are set in `.env` or exported to current shell session
- Verify `php -S localhost:8080` was started in the same shell where you set env vars

**CORS errors**
- The redirect URI must match exactly what's registered in your Mobiscroll OAuth app
- Default is `http://localhost:8080`

## Testing

1. Start server: `php -S localhost:8080 -t public`
2. Get auth URL: `curl http://localhost:8080/?action=auth-url`
3. Open authUrl in browser and authorize
4. After redirect, check calendars: `curl http://localhost:8080/?action=calendars`
