# Mobiscroll Connect Ruby SDK

Ruby client for [Mobiscroll Connect](https://mobiscroll.com/connect), the calendar connectivity layer for scheduling products — Google Calendar, Microsoft Outlook, Apple Calendar and CalDAV through one API. Backend only — works with your own UI.

[![RubyGems](https://img.shields.io/gem/v/mobiscroll-connect?label=RubyGems)](https://rubygems.org/gems/mobiscroll-connect)

**[RubyGems](https://rubygems.org/gems/mobiscroll-connect)** · **[Documentation](https://mobiscroll.com/docs/connect/ruby-sdk)** · **[Changelog](https://github.com/acidb/mobiscroll-connect-sdks/blob/main/sdks/ruby/CHANGELOG.md)** · **[Source](https://github.com/acidb/mobiscroll-connect-sdks/tree/main/sdks/ruby)**

## Installation

Add to your `Gemfile`:

```ruby
gem 'mobiscroll-connect', '~> 1.0'
```

Or install directly:

```bash
gem install mobiscroll-connect
```

## Quick start

```ruby
require 'mobiscroll-connect'

client = Mobiscroll::Connect::Client.new(
  client_id:     ENV['MOBISCROLL_CLIENT_ID'],
  client_secret: ENV['MOBISCROLL_CLIENT_SECRET'],
  redirect_uri:  'https://yourapp.com/oauth/callback'
)
```

## OAuth flow

### 1. Generate the authorization URL

```ruby
url = client.auth.generate_auth_url(
  user_id:   'user-123',
  lng:       'es', # optional: Connect page language, see https://mobiscroll.com/docs/connect/localization#supported-languages
  providers: [
    Mobiscroll::Connect::Provider::GOOGLE,
    Mobiscroll::Connect::Provider::MICROSOFT
  ]
)
# Redirect the user to `url`
```

### 2. Exchange the code for tokens

```ruby
# In your /oauth/callback handler:
tokens = client.auth.get_token(params[:code])
# tokens.access_token, tokens.refresh_token, tokens.expires_in
```

### 3. Restore credentials on subsequent requests

```ruby
client.set_credentials(
  Mobiscroll::Connect::TokenResponse.new(
    access_token:  session[:access_token],
    refresh_token: session[:refresh_token],
    token_type:    'Bearer'
  )
)
```

### 4. Check connection status

```ruby
status = client.auth.get_connection_status
status.connections.each do |provider, accounts|
  accounts.each do |a|
    puts "#{provider}: #{a.display}"
    # Google's consent screen lets the user untick the calendar permission and still
    # finish signing in. Such an account is connected but lists no calendars.
    puts "  #{a.id} must reconnect and allow calendar access" if a.calendar_permission_granted == false
  end
end
```

### 5. Disconnect a provider

```ruby
client.auth.disconnect(provider: Mobiscroll::Connect::Provider::GOOGLE)
```

## Token refresh

The SDK automatically refreshes expired access tokens. When a request returns 401 and a `refresh_token` is available, the SDK:

1. Calls `POST /oauth/token` with `grant_type=refresh_token` (exactly once).
2. Retries the original request with the new token.
3. Raises `AuthenticationError` if the refresh also fails.

Concurrent 401s share a single in-flight refresh — only one `POST /oauth/token` is ever issued per `Client` instance at a time.

**Running more than one instance.** The single in-flight refresh above is per `Client` instance. The SDK does not coordinate across processes — separate containers, cluster workers or serverless invocations each refresh from the tokens they hold in memory. Connect accepts concurrent refreshes of the same token, but refreshing with a copy that is two or more refreshes out of date revokes the user's authorization. Persist refreshed tokens to storage every instance reads, and call `client.set_credentials(...)` with the current tokens when a process starts a job or handles a request. See [Refreshing from several instances](https://mobiscroll.com/docs/connect/api/oauth#concurrent-refresh).

To persist refreshed tokens (e.g., back to a session or database):

```ruby
client.on_tokens_refreshed do |tokens|
  session[:access_token]  = tokens.access_token
  session[:refresh_token] = tokens.refresh_token if tokens.refresh_token
end
```

## Calendars

```ruby
calendars = client.calendars.list
calendars.each do |cal|
  puts "#{cal.provider} / #{cal.title} (#{cal.id})"
end
```

## Events

### List events

```ruby
result = client.events.list(
  start:         '2024-01-01T00:00:00Z',
  end:           '2024-03-31T23:59:59Z',
  page_size:     50,
  single_events: true,
  calendar_ids:  { 'google' => ['primary'] }
)
result.events.each { |e| puts e.title }
# result.next_page_token for pagination
```

### Create an event

```ruby
event = client.events.create(
  provider:    Mobiscroll::Connect::Provider::GOOGLE,
  calendar_id: 'primary',
  title:       'Team Meeting',
  start:       '2024-02-01T10:00:00Z',
  end:         '2024-02-01T11:00:00Z',
  description: 'Quarterly review',
  recurrence:  Mobiscroll::Connect::RecurrenceRule.new(
    frequency: 'WEEKLY',
    interval:  1,
    count:     10
  )
)
puts event.id
```

### Update an event

```ruby
client.events.update(
  provider:    'google',
  calendar_id: 'primary',
  event_id:    'evt-123',
  title:       'Updated Title',
  update_mode: 'this'
)
```

### Delete an event

```ruby
client.events.delete(
  provider:    'google',
  calendar_id: 'primary',
  event_id:    'evt-123',
  delete_mode: 'all'
)
```

## Webhooks

### Subscribe to calendar change notifications

```ruby
result = client.webhooks.subscribe_webhook(
  provider:    Mobiscroll::Connect::Provider::GOOGLE,
  calendar_id: 'primary'
)
puts result.channel_id
puts result.server_webhook_url
```

### Unsubscribe

```ruby
client.webhooks.unsubscribe_webhook(
  provider:    'google',
  channel_id:  result.channel_id,
  resource_id: result.subscription.resource_id
)
```

### Verify webhook deliveries

Every delivery to your webhook URL is signed. `verify_webhook` checks the signature and the timestamp, then returns the parsed `WebhookDelivery`, or raises `WebhookVerificationError`. Pass the **raw** request body: a JSON body parser that runs first changes the bytes and every check fails.

```ruby
post '/webhooks/mobiscroll' do
  begin
    delivery = client.webhooks.verify_webhook(request.body.read, request.env)
  rescue Mobiscroll::Connect::WebhookVerificationError => e
    # 503 lets Connect retry when the keys could not be loaded; 401 is final.
    halt(e.reason == 'no_public_keys' ? 503 : 401)
  end
  handle_delivery(delivery)
  status 204
end
```

`headers` can be a Hash with any key casing, a Rack env (`HTTP_WEBHOOK_ID` keys), or Rails' `request.headers` (pass `request.raw_post` as the body in Rails).

The public keys are fetched from `https://connect.mobiscroll.com/.well-known/webhook-keys` on the first delivery and cached for the whole process as the endpoint's `Cache-Control` allows. If no signature matches, the keys are fetched again (at most once a minute) before the delivery is rejected, so key rotations need no action on your side.

If your handler cannot make outbound requests, pin the key. `webhook_public_key` is used only when the keys endpoint cannot be reached; it stops working when Mobiscroll retires that key, so you must replace it on every rotation.

```ruby
client = Mobiscroll::Connect::Client.new(
  client_id:          ENV['MOBISCROLL_CLIENT_ID'],
  client_secret:      ENV['MOBISCROLL_CLIENT_SECRET'],
  redirect_uri:       ENV['MOBISCROLL_REDIRECT_URI'],
  webhook_public_key: ENV['MOBISCROLL_WEBHOOK_PUBLIC_KEY'] # whpk_...
)
```

To check against keys you supply, with no fetching, call `Mobiscroll::Connect.verify_webhook_signature(raw_body, headers, ['whpk_...'])`. It raises `WebhookVerificationError` and returns `nil`. See [Verifying deliveries](https://mobiscroll.com/docs/connect/api/webhooks#verifying-deliveries).

## Error handling

All errors are subclasses of `Mobiscroll::Connect::Error`:

```ruby
begin
  client.calendars.list
rescue Mobiscroll::Connect::AuthenticationError => e
  puts "Auth failed: #{e.message}"
rescue Mobiscroll::Connect::RateLimitError => e
  puts "Rate limited — retry after #{e.retry_after}s"
rescue Mobiscroll::Connect::ValidationError => e
  puts "Bad request: #{e.message}, details: #{e.details}"
rescue Mobiscroll::Connect::NotFoundError
  puts 'Resource not found'
rescue Mobiscroll::Connect::ServerError => e
  puts "Server error #{e.status_code}"
rescue Mobiscroll::Connect::NetworkError => e
  puts "Network error: #{e.message}"
rescue Mobiscroll::Connect::Error => e
  puts "SDK error: #{e.message} (#{e.code})"
end
```

| Error class | HTTP status | Extra attributes |
|---|---|---|
| `AuthenticationError` | 401, 403 | — |
| `ValidationError` | 400, 422 | `details` |
| `NotFoundError` | 404 | — |
| `RateLimitError` | 429 | `retry_after` (seconds) |
| `ServerError` | 5xx | `status_code` |
| `NetworkError` | transport | `cause` |
| `WebhookVerificationError` | — (from `verify_webhook`) | `reason` |

## Minimal demo app

See [`minimal-app/`](minimal-app/) for a Sinatra web app demonstrating the full OAuth flow. Run it with:

```bash
cd minimal-app
bundle install
cp .env.example .env
bundle exec rackup -p 8080
```

## Development

```bash
bundle install
bundle exec rspec        # tests
bundle exec rubocop      # lint
gem build mobiscroll-connect.gemspec
```

## License

MIT. See [LICENSE](LICENSE).
