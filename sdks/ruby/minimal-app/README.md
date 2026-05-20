# Mobiscroll Connect Ruby SDK — Minimal Demo App

A minimal Sinatra web app demonstrating the OAuth flow and calendar/event management with the `mobiscroll-connect` gem.

## How to run

```bash
# From this directory (sdks/ruby/minimal-app):
bundle install
cp .env.example .env
# Edit .env with your Mobiscroll Connect credentials
bundle exec rackup -p 8080
```

Open [http://localhost:8080](http://localhost:8080) and click **Connect calendar account** to start the OAuth flow.
