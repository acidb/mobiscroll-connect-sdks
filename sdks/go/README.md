# Mobiscroll Connect — Go SDK

Official Go client for the [Mobiscroll Connect](https://mobiscroll.com/connect) API. Same public surface as the Node, Python, PHP, .NET, and Java SDKs in this monorepo.

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
```

The `mobiscroll.Ptr` helper exists so you can fill in optional `*T` fields inline without declaring a local variable just to take its address.

## Configuration

`NewClient` accepts functional options:

| Option | Default | Purpose |
|---|---|---|
| `WithBaseURL(url)` | `https://connect.mobiscroll.com/api` | Override for staging / mock servers (no trailing slash). |
| `WithTimeout(d)` | `30 * time.Second` | Per-request timeout. |
| `WithHTTPClient(c)` | — | Inject a pre-built `*http.Client` (custom transport, proxy, etc.). |
| `WithTokensRefreshedCallback(fn)` | — | Called after each successful token refresh; persist new tokens here. |

`client.SetCredentials(tokens)` stores tokens manually (e.g. on restart). `client.OnTokensRefreshed(fn)` overrides the constructor-level callback per request — useful in web handlers where each request writes refreshed tokens back to a cookie.

## Token refresh

The SDK auto-refreshes expired access tokens once per request, then retries the original call. Concurrent requests that all hit 401 at the same time deduplicate into a single refresh via `singleflight`. If refresh fails the caller sees an `*AuthenticationError`.

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
