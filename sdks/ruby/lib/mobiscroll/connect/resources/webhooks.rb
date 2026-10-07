# frozen_string_literal: true

require 'json'
require 'uri'

module Mobiscroll
  module Connect
    module Resources
      class Webhooks
        RETRYABLE_REASONS = %w[no_matching_signature no_public_keys].freeze

        def initialize(config, api_client)
          @config = config
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

        # Verifies that a webhook delivery came from Mobiscroll Connect, and parses it.
        #
        # Fetches the public keys from `/.well-known/webhook-keys` on first use and caches them for
        # the whole process, refreshing them as the endpoint's `Cache-Control` allows. When no
        # signature matches, it re-fetches the keys once (at most once a minute) before rejecting,
        # so a key rotation never rejects genuine deliveries. `webhook_public_key` from the config
        # is used only when the endpoint cannot be reached.
        #
        # `payload` is the raw request body String, exactly as received; not a parsed Hash.
        # `headers` is a Hash (any key casing) or a Rack env / Rails `request.headers`.
        #
        # Returns a WebhookDelivery. Raises WebhookVerificationError when the delivery is not
        # genuine; respond with a 4xx.
        #
        #   post '/webhooks/mobiscroll' do
        #     delivery = client.webhooks.verify_webhook(request.body.read, request.env)
        #     handle_delivery(delivery)
        #     status 204
        #   rescue Mobiscroll::Connect::WebhookVerificationError
        #     halt 401
        #   end
        def verify_webhook(payload, headers)
          store = WebhookKeyStore.for_url(URI.join(@config.base_url, WEBHOOK_KEYS_PATH).to_s)

          begin
            Connect.verify_webhook_signature(payload, headers, keys_or_pinned(store.keys))
          rescue WebhookVerificationError => e
            raise unless RETRYABLE_REASONS.include?(e.reason) && store.can_refetch?

            store.refresh
            Connect.verify_webhook_signature(payload, headers, keys_or_pinned(store.keys))
          end

          parse_delivery(payload)
        end

        private

        # The pinned key is never merged with fetched keys: after an emergency rotation a pinned
        # retired key must stop verifying.
        def keys_or_pinned(keys)
          pinned = @config.webhook_public_key
          keys.empty? && pinned && !pinned.empty? ? [pinned] : keys
        end

        def parse_delivery(payload)
          parsed = begin
            JSON.parse(payload.dup.force_encoding(Encoding::UTF_8))
          rescue JSON::ParserError, EncodingError
            nil
          end
          return WebhookDelivery.from_h(parsed) if parsed.is_a?(Hash)

          raise WebhookVerificationError.new('Webhook payload is not a JSON object', reason: 'invalid_payload')
        end
      end
    end
  end
end
