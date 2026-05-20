# frozen_string_literal: true

module Mobiscroll
  module Connect
    # OAuth2 token payload returned by the API.
    TokenResponse = Struct.new(:access_token, :token_type, :expires_in, :refresh_token, keyword_init: true) do
      def self.from_h(hash)
        return nil if hash.nil?

        new(
          access_token: hash['access_token'] || hash[:access_token],
          token_type: hash['token_type'] || hash[:token_type],
          expires_in: hash['expires_in'] || hash[:expires_in],
          refresh_token: hash['refresh_token'] || hash[:refresh_token]
        )
      end

      # Overlay `incoming` on top of self, preserving the existing
      # refresh_token if `incoming` omits one.
      def merged_with(incoming)
        return incoming if self.nil?

        rt = incoming.refresh_token
        rt = refresh_token if rt.nil? || rt.empty?
        TokenResponse.new(
          access_token: incoming.access_token,
          token_type: incoming.token_type || token_type,
          expires_in: incoming.expires_in || expires_in,
          refresh_token: rt
        )
      end

      def to_h
        super.compact
      end
    end

    # One connected account under a provider.
    ConnectedAccount = Struct.new(:id, :display, keyword_init: true) do
      def self.from_h(hash)
        return nil if hash.nil?

        new(id: hash['id'] || hash[:id], display: hash['display'] || hash[:display])
      end
    end

    # Result of Auth#get_connection_status. `connections` is keyed by lowercase
    # provider name to match the API wire form.
    ConnectionStatus = Struct.new(:connections, :limit_reached, keyword_init: true) do
      def self.from_h(hash)
        return new(connections: {}, limit_reached: false) if hash.nil?

        raw = hash['connections'] || hash[:connections] || {}
        connections = raw.each_with_object({}) do |(provider, accounts), acc|
          acc[provider.to_s] = Array(accounts).map { |a| ConnectedAccount.from_h(a) }
        end
        new(connections: connections, limit_reached: hash['limitReached'] || hash[:limitReached] || false)
      end
    end

    DisconnectResponse = Struct.new(:success, :message, keyword_init: true) do
      def self.from_h(hash)
        return nil if hash.nil?

        new(success: hash['success'] || hash[:success], message: hash['message'] || hash[:message])
      end
    end

    # A calendar exposed by one of the supported providers.
    Calendar = Struct.new(:provider, :id, :title, :time_zone, :color, :description, :original, keyword_init: true) do
      def self.from_h(hash)
        return nil if hash.nil?

        new(
          provider: hash['provider'] || hash[:provider],
          id: hash['id'] || hash[:id],
          title: hash['title'] || hash[:title],
          time_zone: hash['timeZone'] || hash[:timeZone] || hash['time_zone'],
          color: hash['color'] || hash[:color],
          description: hash['description'] || hash[:description],
          original: hash['original'] || hash[:original]
        )
      end
    end

    Attendee = Struct.new(:email, :status, :organizer, keyword_init: true) do
      def self.from_h(hash)
        return nil if hash.nil?

        new(
          email: hash['email'] || hash[:email],
          status: hash['status'] || hash[:status],
          organizer: hash['organizer'] || hash[:organizer]
        )
      end

      def to_h
        result = { 'email' => email }
        result['status'] = status unless status.nil?
        result['organizer'] = organizer unless organizer.nil?
        result
      end
    end

    # An event returned by the API. Field set mirrors the Node SDK exactly.
    CalendarEvent = Struct.new(
      :provider, :id, :calendar_id, :title, :description, :start, :end_time, :all_day,
      :recurring_event_id, :color, :location, :attendees, :custom, :conference,
      :conference_data, :availability, :privacy, :status, :last_modified, :link, :original,
      keyword_init: true
    ) do
      def self.from_h(hash)
        return nil if hash.nil?

        attendees = hash['attendees'] || hash[:attendees]
        attendees = attendees.map { |a| Attendee.from_h(a) } if attendees.is_a?(Array)

        new(
          provider: hash['provider'] || hash[:provider],
          id: hash['id'] || hash[:id],
          calendar_id: hash['calendarId'] || hash[:calendarId],
          title: hash['title'] || hash[:title],
          description: hash['description'] || hash[:description],
          start: hash['start'] || hash[:start],
          end_time: hash['end'] || hash[:end],
          all_day: hash['allDay'] || hash[:allDay],
          recurring_event_id: hash['recurringEventId'] || hash[:recurringEventId],
          color: hash['color'] || hash[:color],
          location: hash['location'] || hash[:location],
          attendees: attendees,
          custom: hash['custom'] || hash[:custom],
          conference: hash['conference'] || hash[:conference],
          conference_data: hash['conferenceData'] || hash[:conferenceData],
          availability: hash['availability'] || hash[:availability],
          privacy: hash['privacy'] || hash[:privacy],
          status: hash['status'] || hash[:status],
          last_modified: hash['lastModified'] || hash[:lastModified],
          link: hash['link'] || hash[:link],
          original: hash['original'] || hash[:original]
        )
      end
    end

    # iCal-style recurrence rule. `frequency` is one of "DAILY", "WEEKLY",
    # "MONTHLY", "YEARLY".
    RecurrenceRule = Struct.new(
      :frequency, :interval, :count, :until, :by_day, :by_month_day, :by_month,
      keyword_init: true
    ) do
      def to_wire
        wire = { 'frequency' => frequency }
        wire['interval'] = interval unless interval.nil?
        wire['count'] = count unless count.nil?
        wire['until'] = self[:until] unless self[:until].nil?
        wire['byDay'] = by_day unless by_day.nil?
        wire['byMonthDay'] = by_month_day unless by_month_day.nil?
        wire['byMonth'] = by_month unless by_month.nil?
        wire
      end
    end

    # Paginated response from Events#list.
    EventsListResponse = Struct.new(:events, :page_size, :next_page_token, keyword_init: true) do
      def self.from_h(hash)
        return new(events: [], page_size: nil, next_page_token: nil) if hash.nil?

        events = (hash['events'] || hash[:events] || []).map { |e| CalendarEvent.from_h(e) }
        new(
          events: events,
          page_size: hash['pageSize'] || hash[:pageSize],
          next_page_token: hash['nextPageToken'] || hash[:nextPageToken]
        )
      end
    end
  end
end
