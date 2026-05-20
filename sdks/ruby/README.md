# Mobiscroll Connect Ruby SDK

Official Ruby client for the [Mobiscroll Connect API](https://connect.mobiscroll.com). Sync calendar events across Google Calendar, Microsoft Outlook, Apple Calendar, and CalDAV.

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
  accounts.each { |a| puts "#{provider}: #{a.display}" }
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
