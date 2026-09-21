# frozen_string_literal: true

require 'json'

module Mobiscroll
  module Connect
    module Resources
      class Webhooks
        def initialize(_config, api_client)
          @api_client = api_client
        end

        # Subscribes to push notifications for changes on a calendar.
        #
        # `channel_id` is auto-generated server-side when omitted. `expiration` is a
        # provider-specific timestamp (ms epoch) the caller can suggest.
        def subscribe_webhook(provider:, calendar_id:, channel_id: nil, expiration: nil)
          body = { 'provider' => provider, 'calendarId' => calendar_id }
          body['channelId'] = channel_id if channel_id
          body['expiration'] = expiration if expiration

          parsed = @api_client.post('/subscribe-webhook', body: JSON.generate(body))
          SubscribeWebhookResponse.from_h(parsed)
        end

        # Unsubscribes a previously created webhook channel. `resource_id` is required by
        # some providers (e.g. Google) to fully unsubscribe.
        #
        # The backend treats a 200 response as final even when the provider-side
        # unsubscribe itself failed (e.g. an already-expired subscription) — the local
        # mapping is removed either way, and `message` explains what happened.
        def unsubscribe_webhook(provider:, channel_id:, resource_id: nil)
          body = { 'provider' => provider, 'channelId' => channel_id }
          body['resourceId'] = resource_id if resource_id

          parsed = @api_client.post('/unsubscribe-webhook', body: JSON.generate(body))
          UnsubscribeWebhookResponse.from_h(parsed)
        end
      end
    end
  end
end
