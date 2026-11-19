# frozen_string_literal: true

require 'json'

module Mobiscroll
  module Connect
    module Resources
      class Events
        def initialize(_config, api_client)
          @api_client = api_client
        end

        # The wire (camelCase) spelling of an optional field is accepted too, as the Python SDK does.
        WIRE_KEYS = {
          allDay: :all_day, recurringEventId: :recurring_event_id, updateMode: :update_mode,
          conferenceData: :conference_data
        }.freeze

        # Lists calendar events.
        #
        # `single_events` (default true) expands recurring events into occurrences; false returns the
        # series masters. `include_cancelled` (default false) also returns the deleted occurrences of
        # series that still exist, as events with `status` 'cancelled', with `single_events: true`;
        # it is ignored with `single_events: false` and applies to the normal list and to `event_ids`.
        #
        # `event_ids` returns only the given events: a Hash of event id Arrays per provider and
        # calendar id, such as { 'google' => { 'ana@example.com' => ['abc123'] } }, JSON-encoded into
        # the `eventIds` query parameter like `calendar_ids`. It is sent whenever it is not nil, an
        # empty Hash included (sent as `{}`, which the server rejects with a 400, never as an
        # unfiltered list). At most 40 ids per request (more raises ValidationError). It cannot be
        # combined with `calendar_ids` (ValidationError) and is not available with the `free-busy`
        # scope (HTTP 403, AuthenticationError).
        #
        # With `single_events: false`, each event itself comes back (a series' master, or an
        # occurrence), in the order of the ids, without a time window. With `single_events: true`
        # (default), each id returns what the list returns for it inside `start` and `end`: the
        # occurrences of a series, never its master, or a single event when it overlaps the window.
        # Missing or deleted ids are left out, without an error.
        #
        # A `next_page_token` from such a request is only valid with the same `event_ids`,
        # `single_events`, `include_cancelled`, `start` and `end` (anything else raises
        # ValidationError).
        #
        # Returns an EventsListResponse: the events of this page, `page_size`, `next_page_token` when
        # more events follow, and `info` when set.
        #
        #   result = client.events.list(
        #     event_ids: { 'google' => { 'ana@example.com' => %w[abc123 def456] } },
        #     single_events: false
        #   )
        def list(start: nil, end: nil, calendar_ids: nil, page_size: nil,
                 next_page_token: nil, single_events: nil, include_cancelled: nil, event_ids: nil)
          end_time = binding.local_variable_get(:end)
          query = {}
          query['start'] = start if start
          query['end'] = end_time if end_time
          query['pageSize'] = page_size if page_size
          query['nextPageToken'] = next_page_token if next_page_token
          query['singleEvents'] = single_events.to_s unless single_events.nil?
          query['includeCancelled'] = include_cancelled.to_s unless include_cancelled.nil?

          if calendar_ids && !calendar_ids.empty?
            wire = calendar_ids.transform_keys(&:to_s)
            query['calendarIds'] = JSON.generate(wire)
          end

          # Never dropped when empty: the server answers 400, where dropping it would list every event.
          query['eventIds'] = JSON.generate(event_ids.transform_keys(&:to_s)) unless event_ids.nil?

          parsed = @api_client.get('/events', query: query)
          EventsListResponse.from_h(parsed)
        end

        def create(provider:, calendar_id:, title:, start:, end:, **opts)
          end_time = binding.local_variable_get(:end)
          body = build_event_body(opts.merge(
                                    provider: provider,
                                    calendar_id: calendar_id,
                                    title: title,
                                    start: start,
                                    end: end_time
                                  ))
          parsed = @api_client.post('/event', body: JSON.generate(body))
          CalendarEvent.from_h(parsed)
        end

        def update(provider:, calendar_id:, event_id:, **opts)
          body = build_event_body(opts.merge(
                                    provider: provider,
                                    calendar_id: calendar_id,
                                    event_id: event_id
                                  ))
          parsed = @api_client.put('/event', body: JSON.generate(body))
          CalendarEvent.from_h(parsed)
        end

        def delete(provider:, calendar_id:, event_id:, recurring_event_id: nil, delete_mode: nil)
          query = {
            'provider' => provider,
            'calendarId' => calendar_id,
            'eventId' => event_id
          }
          query['recurringEventId'] = recurring_event_id if recurring_event_id
          query['deleteMode'] = delete_mode if delete_mode

          @api_client.delete('/event', query: query)
          nil
        end

        private

        def build_event_body(params)
          params = params.to_h { |key, value| [WIRE_KEYS.fetch(key.to_sym, key.to_sym), value] }
          body = {}
          body['provider'] = params[:provider] if params.key?(:provider)
          body['calendarId'] = params[:calendar_id] if params.key?(:calendar_id)
          body['eventId'] = params[:event_id] if params.key?(:event_id)
          body['title'] = params[:title] if params.key?(:title)
          body['start'] = params[:start] if params.key?(:start)
          body['end'] = params[:end] if params.key?(:end)
          body['allDay'] = params[:all_day] if params.key?(:all_day)
          body['description'] = params[:description] if params.key?(:description)
          body['location'] = params[:location] if params.key?(:location)
          body['recurringEventId'] = params[:recurring_event_id] if params.key?(:recurring_event_id)
          body['updateMode'] = params[:update_mode] if params.key?(:update_mode)
          body['availability'] = params[:availability] if params.key?(:availability)
          body['privacy'] = params[:privacy] if params.key?(:privacy)
          body['status'] = params[:status] if params.key?(:status)

          if params.key?(:recurrence)
            body['recurrence'] =
              params[:recurrence].is_a?(RecurrenceRule) ? params[:recurrence].to_wire : params[:recurrence]
          end

          body['attendees'] = Array(params[:attendees]).map { |a| attendee_email(a) } if params.key?(:attendees)

          body['conference'] = params[:conference] if params.key?(:conference)
          body['conferenceData'] = params[:conference_data] if params.key?(:conference_data)
          body['custom'] = params[:custom] if params.key?(:custom)
          body
        end

        # The API takes attendees as email strings; an Attendee or a Hash is reduced to its email.
        def attendee_email(attendee)
          case attendee
          when Attendee then attendee.email
          when Hash then attendee['email'] || attendee[:email]
          else attendee
          end
        end
      end
    end
  end
end
