# frozen_string_literal: true

require 'base64'
require 'faraday'
require 'json'
require 'monitor'

module Mobiscroll
  module Connect
    # Internal HTTP layer. Mirrors sdks/node/src/client.ts and sdks/go/transport.go:
    # - injects `Authorization: Bearer <access_token>` on every API request
    # - on 401 with a stored refresh_token, refreshes once and retries the original
    #   call exactly once. Concurrent 401s share a single in-flight refresh.
    # - maps non-2xx responses to typed errors via `Connect.map_response_error`.
    #
    # The token-exchange / refresh Faraday connection is held separately so it
    # cannot recurse into the 401 retry loop.
    class ApiClient
      attr_reader :config

      def initialize(config)
        @config = config
        @credentials = nil
        @monitor = Monitor.new
        @refresh_cond = @monitor.new_cond
        @refresh_in_flight = false
        @refresh_result = nil
        @refresh_error = nil
        @on_tokens_refreshed = config.on_tokens_refreshed

        @api_conn = build_connection(api: true)
        @token_conn = build_connection(api: false)
      end

      def set_credentials(tokens)
        @monitor.synchronize { @credentials = tokens }
      end

      def credentials
        @monitor.synchronize { @credentials }
      end

      def on_tokens_refreshed(&block)
        @on_tokens_refreshed = block
      end

      def get(path, query: nil, headers: nil)
        execute(:get, path, query: query, body: nil, headers: headers)
      end

      def post(path, body: nil, query: nil, headers: nil)
        execute(:post, path, query: query, body: body, headers: headers)
      end

      def put(path, body: nil, query: nil, headers: nil)
        execute(:put, path, query: query, body: body, headers: headers)
      end

      def delete(path, query: nil, headers: nil)
        execute(:delete, path, query: query, body: nil, headers: headers)
      end

      # POST application/x-www-form-urlencoded against the token endpoint with
      # Basic auth + CLIENT_ID header. Does not participate in the 401 retry
      # loop because it uses `@token_conn`.
      def post_form(path, form)
        creds = Base64.strict_encode64("#{@config.client_id}:#{@config.client_secret}")
        response = @token_conn.post(path.sub(%r{\A/}, '')) do |req|
          req.headers['Content-Type'] = 'application/x-www-form-urlencoded'
          req.headers['Authorization'] = "Basic #{creds}"
          req.headers['CLIENT_ID'] = @config.client_id
          req.body = URI.encode_www_form(form)
        end
        parsed = parse_body(response.body)
        raise_for_status(response, parsed)
        parsed
      rescue Faraday::TimeoutError, Faraday::ConnectionFailed => e
        raise NetworkError.new(e.message, cause: e)
      end

      private

      def execute(method, path, query:, body:, headers:, retried: false)
        response = perform(method, path, query: query, body: body, headers: headers)
        parsed = parse_body(response.body)

        if response.status == 401 && @credentials&.refresh_token && !retried
          new_tokens = refresh_access_token!
          raise AuthenticationError.new('Failed to refresh token') if new_tokens.nil?

          return execute(method, path, query: query, body: body, headers: headers, retried: true)
        end

        raise_for_status(response, parsed)
        parsed
      rescue Faraday::TimeoutError, Faraday::ConnectionFailed => e
        raise NetworkError.new(e.message, cause: e)
      end

      def perform(method, path, query:, body:, headers:)
        @api_conn.public_send(method, path.sub(%r{\A/}, '')) do |req|
          req.params.update(query) if query.is_a?(Hash) && !query.empty?
          token = @credentials&.access_token
          req.headers['Authorization'] = "Bearer #{token}" if token && !token.empty?
          headers&.each { |k, v| req.headers[k] = v }
          req.body = body.is_a?(String) ? body : JSON.generate(body) if body
        end
      end

      def parse_body(body)
        return nil if body.nil? || (body.respond_to?(:empty?) && body.empty?)
        return body if body.is_a?(Hash) || body.is_a?(Array)

        JSON.parse(body.to_s)
      rescue JSON::ParserError
        nil
      end

      def raise_for_status(response, parsed)
        return if response.status < 400

        err = Connect.map_response_error(response.status, parsed, response.headers)
        raise(err) if err

        raise Error.new("HTTP #{response.status}")
      end

      # Refreshes the access token. Concurrent callers share one in-flight
      # request — the first caller does the work, subsequent callers wait on
      # the same condition variable and read the cached result. Returns the
      # refreshed TokenResponse, or nil if refresh failed.
      def refresh_access_token!
        do_refresh = false
        @monitor.synchronize do
          if @refresh_in_flight
            @refresh_cond.wait_while { @refresh_in_flight }
            raise @refresh_error if @refresh_error

            return @refresh_result
          end

          @refresh_in_flight = true
          @refresh_result = nil
          @refresh_error = nil
          do_refresh = true
        end

        return unless do_refresh

        begin
          new_tokens = perform_refresh
          @monitor.synchronize do
            @credentials = (@credentials ? @credentials.merged_with(new_tokens) : new_tokens)
            @refresh_result = @credentials
          end
          @on_tokens_refreshed&.call(@credentials)
          @credentials
        rescue StandardError => e
          @monitor.synchronize { @refresh_error = e }
          nil
        ensure
          @monitor.synchronize do
            @refresh_in_flight = false
            @refresh_cond.broadcast
          end
        end
      end

      def perform_refresh
        rt = @credentials&.refresh_token
        raise AuthenticationError.new('No refresh token available') if rt.nil? || rt.empty?

        form = {
          'grant_type' => 'refresh_token',
          'refresh_token' => rt,
          'redirect_uri' => @config.redirect_uri
        }
        parsed = post_form('/oauth/token', form)
        TokenResponse.from_h(parsed)
      end

      def build_connection(api:)
        Faraday.new(url: @config.base_url) do |f|
          f.options.timeout = @config.timeout
          f.options.open_timeout = @config.timeout
          f.headers['Content-Type'] = 'application/json' if api
          f.headers['Accept'] = 'application/json'
        end
      end
    end
  end
end
