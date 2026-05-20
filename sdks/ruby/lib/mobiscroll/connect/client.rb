# frozen_string_literal: true

module Mobiscroll
  module Connect
    class Client
      attr_reader :auth, :calendars, :events

      def initialize(client_id:, client_secret:, redirect_uri:,
                     base_url: DEFAULT_BASE_URL, timeout: DEFAULT_TIMEOUT,
                     on_tokens_refreshed: nil)
        @config = Config.new(
          client_id: client_id,
          client_secret: client_secret,
          redirect_uri: redirect_uri,
          base_url: base_url,
          timeout: timeout,
          on_tokens_refreshed: on_tokens_refreshed
        )
        @api_client = ApiClient.new(@config)
        @auth = Resources::Auth.new(@config, @api_client)
        @calendars = Resources::Calendars.new(@config, @api_client)
        @events = Resources::Events.new(@config, @api_client)
      end

      def set_credentials(tokens)
        @api_client.set_credentials(tokens)
      end

      def credentials
        @api_client.credentials
      end

      def on_tokens_refreshed(&block)
        @api_client.on_tokens_refreshed(&block)
      end
    end
  end
end
