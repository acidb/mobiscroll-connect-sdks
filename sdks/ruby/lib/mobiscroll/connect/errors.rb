# frozen_string_literal: true

module Mobiscroll
  module Connect
    class Error < StandardError
      attr_reader :code

      def initialize(message = nil, code: nil)
        super(message)
        @code = code
      end
    end

    class AuthenticationError < Error
      def initialize(message = 'Authentication failed')
        super(message, code: 'AUTHENTICATION_ERROR')
      end
    end

    class NotFoundError < Error
      def initialize(message = 'Resource not found')
        super(message, code: 'NOT_FOUND_ERROR')
      end
    end

    class ValidationError < Error
      attr_reader :details

      def initialize(message = 'Validation failed', details: nil)
        super(message, code: 'VALIDATION_ERROR')
        @details = details
      end
    end

    class RateLimitError < Error
      attr_reader :retry_after

      def initialize(message = 'Rate limit exceeded', retry_after: nil)
        super(message, code: 'RATE_LIMIT_ERROR')
        @retry_after = retry_after
      end
    end

    class ServerError < Error
      attr_reader :status_code

      def initialize(message = 'Server error', status_code: nil)
        super(message, code: 'SERVER_ERROR')
        @status_code = status_code
      end
    end

    class NetworkError < Error
      attr_reader :cause

      def initialize(message = 'Network error', cause: nil)
        super(message, code: 'NETWORK_ERROR')
        @cause = cause
      end
    end

    # Maps an HTTP response (status + body hash + headers) to the matching
    # typed error. Returns nil for 2xx responses.
    def self.map_response_error(status, body, headers)
      message = body.is_a?(Hash) ? (body['message'] || body[:message]) : nil
      message ||= "HTTP #{status}"

      case status
      when 401, 403
        AuthenticationError.new(message)
      when 404
        NotFoundError.new(message)
      when 400, 422
        details = body.is_a?(Hash) ? (body['details'] || body[:details]) : nil
        ValidationError.new(message, details: details)
      when 429
        retry_after = headers && (headers['retry-after'] || headers['Retry-After'])
        retry_after_int = retry_after&.to_i
        RateLimitError.new(message, retry_after: retry_after_int)
      when 500..599
        ServerError.new(message, status_code: status)
      end
    end
  end
end
