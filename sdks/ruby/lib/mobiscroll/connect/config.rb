# frozen_string_literal: true

module Mobiscroll
  module Connect
    DEFAULT_BASE_URL = 'https://connect.mobiscroll.com/api'
    DEFAULT_TIMEOUT = 30

    class Config
      attr_reader :client_id, :client_secret, :redirect_uri, :base_url, :timeout
      attr_accessor :on_tokens_refreshed

      def initialize(client_id:, client_secret:, redirect_uri:,
                     base_url: DEFAULT_BASE_URL, timeout: DEFAULT_TIMEOUT,
                     on_tokens_refreshed: nil)
        raise Error, 'client_id is required' if client_id.nil? || client_id.empty?
        raise Error, 'client_secret is required' if client_secret.nil? || client_secret.empty?
        raise Error, 'redirect_uri is required' if redirect_uri.nil? || redirect_uri.empty?

        @client_id = client_id
        @client_secret = client_secret
        @redirect_uri = redirect_uri
        @base_url = base_url
        @timeout = timeout
        @on_tokens_refreshed = on_tokens_refreshed
      end
    end
  end
end
