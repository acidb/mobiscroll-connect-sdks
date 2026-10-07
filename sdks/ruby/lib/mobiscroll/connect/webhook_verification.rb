# frozen_string_literal: true

require 'base64'
require 'faraday'
require 'json'
require 'openssl'

module Mobiscroll
  module Connect
    WEBHOOK_TOLERANCE_SECONDS = 300
    WEBHOOK_KEYS_PATH = '/.well-known/webhook-keys'

    # Verifies the Standard Webhooks `v1a` (Ed25519) signature of a Mobiscroll Connect delivery
    # against the given public keys, without fetching anything.
    #
    # Use it with a pinned key in handlers that cannot make outbound requests. Otherwise prefer
    # `client.webhooks.verify_webhook`, which fetches and refreshes the keys for you.
    #
    # `payload` is the raw request body String, exactly as received. `headers` is a Hash (any
    # key casing, Array values take the first) or a Rack env / Rails `request.headers`, where
    # `HTTP_WEBHOOK_ID`-style keys are matched too. `public_keys` are `whpk_` keys; the delivery
    # is genuine if any signature verifies against any key. `now` is the current Unix time in
    # seconds, defaulting to the system clock.
    #
    # Returns nil. Raises WebhookVerificationError when the delivery is not genuine or cannot
    # be checked.
    #
    #   Mobiscroll::Connect.verify_webhook_signature(
    #     request.body.read, request.env, [ENV.fetch('MOBISCROLL_WEBHOOK_PUBLIC_KEY')]
    #   )
    def self.verify_webhook_signature(payload, headers, public_keys,
                                      tolerance_seconds: WEBHOOK_TOLERANCE_SECONDS, now: nil)
      WebhookSignature.verify(payload, headers, public_keys, tolerance_seconds: tolerance_seconds, now: now)
      nil
    end

    # @api private
    module WebhookSignature
      PUBLIC_KEY_PREFIX = 'whpk_'
      SIGNATURE_VERSION = 'v1a'
      # DER SubjectPublicKeyInfo header for an Ed25519 key; `OpenSSL::PKey.new_raw_public_key`
      # needs openssl gem 3.2+, which Ruby 3.2 does not bundle.
      ED25519_SPKI_PREFIX = ['302a300506032b6570032100'].pack('H*').freeze

      module_function

      def verify(payload, headers, public_keys, tolerance_seconds:, now:)
        unless payload.is_a?(String)
          raise WebhookVerificationError.new(
            'Pass the raw request body as a String, not a parsed object', reason: 'invalid_payload'
          )
        end

        id = read_header(headers, 'webhook-id')
        timestamp = read_header(headers, 'webhook-timestamp')
        signature_header = read_header(headers, 'webhook-signature')
        if [id, timestamp, signature_header].any? { |value| value.nil? || value.empty? }
          raise WebhookVerificationError.new(
            'Missing webhook-id, webhook-timestamp or webhook-signature header', reason: 'missing_headers'
          )
        end

        check_timestamp(timestamp, tolerance_seconds, now || Time.now.to_i)

        keys = Array(public_keys).filter_map { |key| parse_public_key(key) }
        if keys.empty?
          raise WebhookVerificationError.new('No valid webhook public keys available', reason: 'no_public_keys')
        end

        signed_content = "#{id}.#{timestamp}.".b << payload.b
        return if signature_header.split(/ /).any? { |entry| entry_matches?(entry, signed_content, keys) }

        raise WebhookVerificationError.new('No webhook signature matched', reason: 'no_matching_signature')
      end

      def check_timestamp(timestamp, tolerance_seconds, now)
        unless timestamp.match?(/\A\d+\z/)
          raise WebhookVerificationError.new('Invalid webhook-timestamp header', reason: 'invalid_timestamp')
        end
        return if (now - timestamp.to_i).abs <= tolerance_seconds

        raise WebhookVerificationError.new(
          'Webhook timestamp is outside the allowed tolerance', reason: 'timestamp_out_of_tolerance'
        )
      end

      def read_header(headers, name)
        return nil unless headers.respond_to?(:each)

        rack_name = "HTTP_#{name.upcase.tr('-', '_')}"
        headers.each do |key, value|
          next unless key.to_s.casecmp?(name) || key.to_s.casecmp?(rack_name)

          value = value.first if value.is_a?(Array)
          return value&.to_s&.b
        end
        nil
      end

      def parse_public_key(value)
        return nil unless value.is_a?(String)

        raw = Base64.decode64(value.strip.delete_prefix(PUBLIC_KEY_PREFIX))
        return nil unless raw.bytesize == 32

        OpenSSL::PKey.read(ED25519_SPKI_PREFIX + raw)
      rescue OpenSSL::PKey::PKeyError
        nil
      end

      def entry_matches?(entry, signed_content, keys)
        version, separator, encoded = entry.partition(',')
        return false if separator.empty? || version != SIGNATURE_VERSION

        signature = Base64.decode64(encoded)
        return false unless signature.bytesize == 64

        keys.any? do |key|
          key.verify(nil, signature, signed_content)
        rescue OpenSSL::PKey::PKeyError
          false
        end
      end
    end
    private_constant :WebhookSignature

    # Process-wide cache of the keys published at one keys URL, shared by every client instance.
    # Follows the endpoint's `Cache-Control`, re-fetches at most once a minute, and keeps the last
    # good keys when a fetch fails. Concurrent callers share one in-flight fetch.
    #
    # @api private
    class WebhookKeyStore
      DEFAULT_MAX_AGE = 60 * 60
      MAX_MAX_AGE = 24 * 60 * 60
      MIN_REFETCH_INTERVAL = 60
      FETCH_TIMEOUT = 10

      @stores = {}
      @stores_lock = Mutex.new

      class << self
        def for_url(url)
          @stores_lock.synchronize { @stores[url] ||= new(url) }
        end

        # Clears every cached key set; for tests.
        def reset!
          @stores_lock.synchronize { @stores.clear }
        end
      end

      def initialize(url)
        @url = url
        @keys = [].freeze
        @fetched_at = nil
        @last_attempt_at = nil
        @max_age = DEFAULT_MAX_AGE
        @in_flight = false
        @lock = Mutex.new
        @cond = ConditionVariable.new
      end

      # The current keys, fetching them first when they are missing or stale and a fetch is allowed.
      def keys
        fetch_if { |now| stale?(now) && can_refetch_at?(now) }
        @lock.synchronize { @keys }
      end

      def can_refetch?
        @lock.synchronize { can_refetch_at?(Time.now.to_f) }
      end

      # Fetches the keys unless a fetch was attempted in the last minute; joins one in flight.
      def refresh
        fetch_if { |now| can_refetch_at?(now) }
      end

      private

      def stale?(now)
        @fetched_at.nil? || now - @fetched_at > @max_age
      end

      def can_refetch_at?(now)
        @last_attempt_at.nil? || now - @last_attempt_at >= MIN_REFETCH_INTERVAL
      end

      # Starts a fetch when the block allows it, or waits for the one already in flight; the
      # decision and the in-flight flag are taken under one lock so callers never fetch twice.
      def fetch_if
        @lock.synchronize do
          if @in_flight
            @cond.wait(@lock) while @in_flight
            return
          end
          now = Time.now.to_f
          return unless yield(now)

          @in_flight = true
          @last_attempt_at = now
        end

        result = nil
        begin
          result = fetch_keys
        ensure
          @lock.synchronize do
            if result
              @keys, @max_age = result
              @fetched_at = Time.now.to_f
            end
            @in_flight = false
            @cond.broadcast
          end
        end
      end

      # Returns [keys, max_age], or nil when the fetch fails or yields no usable key. Uses a plain
      # connection: the keys endpoint is public and must never go through the token-refresh path.
      def fetch_keys
        response = Faraday.new(request: { timeout: FETCH_TIMEOUT, open_timeout: FETCH_TIMEOUT }).get(@url) do |req|
          req.headers['Accept'] = 'application/json'
        end
        return nil unless response.success?

        parsed = JSON.parse(response.body.to_s)
        keys = Array(parsed.is_a?(Hash) ? parsed['keys'] : nil).filter_map { |entry| usable_key(entry) }
        keys.empty? ? nil : [keys.freeze, parse_max_age(response.headers['cache-control'])]
      rescue StandardError
        nil
      end

      def usable_key(entry)
        return nil unless entry.is_a?(Hash)

        key = entry['key']
        alg = entry['alg'].to_s
        return nil unless key.is_a?(String) && key.start_with?(WebhookSignature::PUBLIC_KEY_PREFIX)
        return nil unless alg.empty? || alg.casecmp?('ed25519')

        key
      end

      def parse_max_age(cache_control)
        match = /max-age=(\d+)/i.match(cache_control.to_s)
        match ? [match[1].to_i, MAX_MAX_AGE].min : DEFAULT_MAX_AGE
      end
    end
  end
end
