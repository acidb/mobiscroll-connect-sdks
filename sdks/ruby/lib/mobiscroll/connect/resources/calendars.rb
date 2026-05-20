# frozen_string_literal: true

module Mobiscroll
  module Connect
    module Resources
      class Calendars
        def initialize(_config, api_client)
          @api_client = api_client
        end

        def list
          parsed = @api_client.get('/calendars')
          return [] if parsed.nil?

          Array(parsed).map { |c| Calendar.from_h(c) }
        end
      end
    end
  end
end
