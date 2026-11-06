# Mobiscroll Connect — Go SDK

Go client for [Mobiscroll Connect](https://mobiscroll.com/connect), the calendar connectivity layer for scheduling products — Google Calendar, Microsoft Outlook, Apple Calendar and CalDAV through one API. Backend only — works with your own UI.

[![pkg.go.dev](https://pkg.go.dev/badge/github.com/acidb/mobiscroll-connect-sdks/sdks/go.svg)](https://pkg.go.dev/github.com/acidb/mobiscroll-connect-sdks/sdks/go)

**[pkg.go.dev](https://pkg.go.dev/github.com/acidb/mobiscroll-connect-sdks/sdks/go)** · **[Documentation](https://mobiscroll.com/docs/connect/go-sdk)** · **[Changelog](https://github.com/acidb/mobiscroll-connect-sdks/blob/main/sdks/go/CHANGELOG.md)** · **[Source](https://github.com/acidb/mobiscroll-connect-sdks/tree/main/sdks/go)**

The API matches the other Mobiscroll Connect SDKs in this monorepo, adjusted for Go conventions.

- **Module path:** `github.com/acidb/mobiscroll-connect-sdks/sdks/go`
- **Minimum Go:** 1.22
- **HTTP:** stdlib `net/http`
- **One runtime dep:** `golang.org/x/sync/singleflight` (for token-refresh dedup)

## Install

```bash
go get github.com/acidb/mobiscroll-connect-sdks/sdks/go@latest
```

```go
import mobiscroll "github.com/acidb/mobiscroll-connect-sdks/sdks/go"
```

## Quick start

```go
ctx := context.Background()
client := mobiscroll.NewClient("client-id", "client-secret", "https://app.example.com/oauth/callback")

// 1. Send the user to the consent URL.
authURL := client.Auth().GenerateAuthURL(&mobiscroll.AuthURLParams{
    UserID:    "user-1",
    Providers: []mobiscroll.Provider{mobiscroll.ProviderGoogle},
    Lng:       "es", // optional: Connect page language, see https://mobiscroll.com/docs/connect/localization#supported-languages
    Theme:     "dark", // optional: Connect page color scheme, "light" | "dark" | "auto"
})
// http.Redirect(w, r, authURL, http.StatusFound)

// 2. In your callback handler, exchange the code for tokens.
tokens, err := client.Auth().GetToken(ctx, code)

// 3. List calendars.
calendars, err := client.Calendars().List(ctx)

// 4. List events.
start := time.Now()
end := start.AddDate(0, 1, 0)
events, err := client.Events().List(ctx, &mobiscroll.EventListParams{
    Start:    &start,
    End:      &end,
    PageSize: mobiscroll.Ptr(50),
    CalendarIDs: map[mobiscroll.Provider][]string{
        mobiscroll.ProviderGoogle: {"primary"},
    },
})

// 5. Create an event.
created, err := client.Events().Create(ctx, &mobiscroll.EventCreateData{
    Provider:   mobiscroll.ProviderGoogle,
    CalendarID: "primary",
    Title:      "Demo",
    Start:      start,
    End:        start.Add(time.Hour),
})

// 6. Subscribe to change notifications for a calendar. Pass its real ID from
//    Calendars().List; an alias such as "primary" creates a duplicate subscription.
//    A ChannelID you supply that another user already holds fails with HTTP 409.
sub, err := client.Webhooks().SubscribeWebhook(ctx, &mobiscroll.SubscribeWebhookParams{
    Provider:   mobiscroll.ProviderGoogle,
    CalendarID: "work@company.com",
})

// 7. Unsubscribe when you no longer need notifications. Connect replaces the channel
//    ID when it renews a subscription, so use the current one from the latest
//    delivery's Metadata.ChannelID.
_, err = client.Webhooks().UnsubscribeWebhook(ctx, &mobiscroll.UnsubscribeWebhookParams{
    Provider:   mobiscroll.ProviderGoogle,
    ChannelID:  sub.ChannelID,
    ResourceID: sub.Subscription.ResourceID, // required by some providers, e.g. Google
})
```

The `mobiscroll.Ptr` helper exists so you can fill in optional `*T` fields inline without declaring a local variable just to take its address.

## Connection management

```go
status, err := client.Auth().GetConnectionStatus(ctx)

// Disconnect one account (its ID from GetConnectionStatus), or every account of a provider.
// This stops the account's webhook subscriptions and deletes its provider credentials;
// it does not revoke the user's Connect tokens.
_, err = client.Auth().Disconnect(ctx, &mobiscroll.DisconnectParams{
    Provider: mobiscroll.ProviderGoogle,
    Account:  status.Connections[mobiscroll.ProviderGoogle][0].ID,
})
_, err = client.Auth().Disconnect(ctx, &mobiscroll.DisconnectParams{Provider: mobiscroll.ProviderMicrosoft})
```

### Revoke tokens

`Revoke` revokes the whole grant: every Connect access and refresh token of the user. It does not disconnect calendar accounts or stop webhooks, so to remove a user entirely, disconnect each provider first (that needs a valid access token), then revoke.

```go
_, err = client.Auth().Revoke(ctx, "")                 // stored refresh token, else access token; clears the stored credentials
_, err = client.Auth().Revoke(ctx, storedRefreshToken) // a token you pass; stored credentials are kept
```

It returns `Success: true` for any token, including an unknown or already revoked one. With an empty token and no stored credentials it returns a `*ValidationError` without making a request. It never refreshes tokens on a 401.

## Verify webhook deliveries

Every delivery to your webhook URL is signed. `VerifyWebhook` checks the signature and the timestamp, then returns the parsed `*WebhookDelivery`, or a `*WebhookVerificationError`. Pass the **raw** request body: decoding the JSON and re-encoding it changes the bytes and every check fails.

```go
http.HandleFunc("/webhooks/mobiscroll", func(w http.ResponseWriter, r *http.Request) {
    body, err := io.ReadAll(r.Body)
    if err != nil {
        http.Error(w, "bad request", http.StatusBadRequest)
        return
    }
    delivery, err := client.Webhooks().VerifyWebhook(r.Context(), body, r.Header)
    var ve *mobiscroll.WebhookVerificationError
    if errors.As(err, &ve) {
        if ve.Reason == mobiscroll.WebhookNoPublicKeys {
            http.Error(w, "keys unavailable", http.StatusServiceUnavailable) // Connect retries
        } else {
            http.Error(w, "invalid signature", http.StatusUnauthorized) // final
        }
        return
    } else if err != nil {
        http.Error(w, "error", http.StatusInternalServerError)
        return
    }
    w.WriteHeader(http.StatusNoContent)
    go handleDelivery(delivery)
})
```

Each event is a `CalendarEvent` with `ChangeType` set. `Conference` is the join URL, and `ConferenceData` is a `*ConferenceData` (`Provider`, `URL`, `AutoGenerate`, `Original`), as the events endpoints return. A `conference` that is not a string fails with `WebhookInvalidPayload`. `WebhookEvent` is a deprecated alias of `CalendarEvent` and will be removed in 2.0.0.

The public keys are fetched from `https://connect.mobiscroll.com/.well-known/webhook-keys` on the first delivery and cached for the whole process, shared by every `Client`, as the endpoint's `Cache-Control` allows. If no signature matches, the keys are fetched again (at most once a minute) before the delivery is rejected, so key rotations need no action on your side.

If your handler cannot make outbound requests, pin the key. `WithWebhookPublicKey` is used only when the keys endpoint cannot be reached; it stops working when Mobiscroll retires that key, so you must replace it on every rotation.

```go
client := mobiscroll.NewClient(clientID, clientSecret, redirectURI,
    mobiscroll.WithWebhookPublicKey(os.Getenv("MOBISCROLL_WEBHOOK_PUBLIC_KEY")), // whpk_...
)
```

To check against keys you supply, with no fetching, call `mobiscroll.VerifyWebhookSignature(body, r.Header, []string{"whpk_..."})`. It returns `nil` or a `*WebhookVerificationError`; `WithWebhookTolerance` and `WithWebhookNow` adjust the timestamp check. See [Verifying deliveries](https://mobiscroll.com/docs/connect/api/webhooks#verifying-deliveries).

## Configuration

`NewClient` accepts functional options:

| Option | Default | Purpose |
|---|---|---|
| `WithBaseURL(url)` | `https://connect.mobiscroll.com/api` | Override for staging / mock servers (no trailing slash). |
| `WithTimeout(d)` | `30 * time.Second` | Per-request timeout. |
| `WithHTTPClient(c)` | — | Inject a pre-built `*http.Client` (custom transport, proxy, etc.). |
| `WithTokensRefreshedCallback(fn)` | — | Called after each successful token refresh; persist new tokens here. |
| `WithWebhookPublicKey(key)` | — | Pinned `whpk_` key for `VerifyWebhook`, used only when the keys endpoint cannot be reached. |

`client.SetCredentials(tokens)` stores tokens manually (e.g. on restart). `client.OnTokensRefreshed(fn)` overrides the constructor-level callback per request — useful in web handlers where each request writes refreshed tokens back to a cookie.

## Token refresh

The SDK auto-refreshes expired access tokens once per request, then retries the original call. Concurrent requests on the same `Client` that all hit 401 at the same time deduplicate into a single refresh via `singleflight`. If refresh fails the caller sees an `*AuthenticationError`.

**Running more than one instance.** That deduplication covers one `Client` in one process. The SDK does not coordinate across processes — separate containers, cluster workers or serverless invocations each refresh from the tokens they hold in memory. Connect accepts concurrent refreshes of the same token, but refreshing with a copy that is two or more refreshes out of date revokes the user's authorization. Persist refreshed tokens to storage every instance reads, and call `client.SetCredentials(tokens)` with the current tokens when a process starts a job or handles a request. See [Refreshing from several instances](https://mobiscroll.com/docs/connect/api/oauth#concurrent-refresh).

## Error handling

All SDK errors satisfy the `mobiscroll.MobiscrollError` interface. Use `errors.As` to extract the concrete type:

```go
_, err := client.Events().Create(ctx, data)
if err != nil {
    var ve *mobiscroll.ValidationError
    if errors.As(err, &ve) {
        log.Printf("validation: %s — details=%s", ve.Message, ve.Details)
        return
    }
    var rl *mobiscroll.RateLimitError
    if errors.As(err, &rl) {
        time.Sleep(time.Duration(rl.RetryAfter) * time.Second)
        return
    }
    log.Printf("other error: %v", err)
}
```

| HTTP | Error type | Extra field |
|---|---|---|
| 401 / 403 | `*AuthenticationError` | — (after refresh+retry has been exhausted) |
| 404 | `*NotFoundError` | — |
| 400 / 422 | `*ValidationError` | `Details` (`json.RawMessage`) |
| 429 | `*RateLimitError` | `RetryAfter` (`int`, seconds) |
| 5xx | `*ServerError` | `StatusCode` (`int`) |
| Transport (timeout / DNS / reset) | `*NetworkError` | wraps the underlying error (`Unwrap`) |
| — (from `VerifyWebhook` / `VerifyWebhookSignature`) | `*WebhookVerificationError` | `Reason` (why verification failed) |

## Minimal demo app

See [minimal-app/](minimal-app/) for a runnable demo (stdlib `net/http` only). From this directory:

```bash
cd minimal-app
MOBISCROLL_CLIENT_ID=... \
MOBISCROLL_CLIENT_SECRET=... \
MOBISCROLL_REDIRECT_URI=http://localhost:8080/oauth/callback \
go run .
# open http://localhost:8080/
```

## Development

```bash
go test -race -count=1 ./...   # full test suite, race detector on
golangci-lint run              # lint
go build ./...                 # confirm everything compiles
```

CI runs the test suite on Go 1.22, 1.23, and 1.24.

## License

MIT.
