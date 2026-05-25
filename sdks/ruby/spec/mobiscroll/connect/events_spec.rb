# frozen_string_literal: true

require 'spec_helper'
require 'support/mock_server'
require 'json'

RSpec.describe Mobiscroll::Connect::Resources::Events do
  let(:client) { MockServer.client_with_tokens }

  let(:event_fixture) do
    {
      'provider' => 'google',
      'id' => 'evt-1',
      'calendarId' => 'primary',
      'title' => 'Team Meeting',
      'start' => '2024-01-15T10:00:00Z',
      'end' => '2024-01-15T11:00:00Z',
      'allDay' => false
    }
  end

  describe '#list' do
    it 'returns an EventsListResponse' do
      MockServer.stub_json(:get, '/events', {
                             'events' => [event_fixture],
                             'pageSize' => 50,
                             'nextPageToken' => nil
                           })

      result = client.events.list
      expect(result).to be_a(Mobiscroll::Connect::EventsListResponse)
      expect(result.events.length).to eq(1)
      expect(result.events.first.title).to eq('Team Meeting')
      expect(result.events.first.end_time).to eq('2024-01-15T11:00:00Z')
    end

    it 'encodes calendar_ids as a JSON query param' do
      stub = WebMock.stub_request(:get, "#{MockServer::BASE_URL}/events")
                    .with(query: hash_including('calendarIds' => '{"google":["primary"]}'))
                    .to_return(
                      status: 200,
                      body: JSON.generate({ 'events' => [], 'pageSize' => 50 }),
                      headers: { 'Content-Type' => 'application/json' }
                    )

      client.events.list(calendar_ids: { 'google' => ['primary'] })

      expect(stub).to have_been_requested
    end

    it 'supports pagination params' do
      stub = WebMock.stub_request(:get, /\A#{Regexp.escape("#{MockServer::BASE_URL}/events")}/)
                    .with(query: hash_including('pageSize' => '10', 'nextPageToken' => 'page1'))
                    .to_return(
                      status: 200,
                      body: JSON.generate({ 'events' => [], 'nextPageToken' => 'page2' }),
                      headers: { 'Content-Type' => 'application/json' }
                    )

      result = client.events.list(page_size: 10, next_page_token: 'page1')
      expect(stub).to have_been_requested
      _ = result
    end
  end

  describe '#create' do
    it 'creates an event and returns a CalendarEvent' do
      MockServer.stub_json(:post, '/event', event_fixture)

      event = client.events.create(
        provider: 'google',
        calendar_id: 'primary',
        title: 'Team Meeting',
        start: '2024-01-15T10:00:00Z',
        end: '2024-01-15T11:00:00Z'
      )

      expect(event).to be_a(Mobiscroll::Connect::CalendarEvent)
      expect(event.id).to eq('evt-1')
      expect(event.title).to eq('Team Meeting')
    end

    it 'serializes recurrence rule to wire format' do
      stub = WebMock.stub_request(:post, "#{MockServer::BASE_URL}/event")
                    .with { |req| JSON.parse(req.body).dig('recurrence', 'frequency') == 'WEEKLY' }
                    .to_return(
                      status: 200,
                      body: JSON.generate(event_fixture),
                      headers: { 'Content-Type' => 'application/json' }
                    )

      rule = Mobiscroll::Connect::RecurrenceRule.new(
        frequency: 'WEEKLY',
        interval: 1,
        by_day: %w[MO WE]
      )

      client.events.create(
        provider: 'google',
        calendar_id: 'primary',
        title: 'Recurring Meeting',
        start: '2024-01-15T10:00:00Z',
        end: '2024-01-15T11:00:00Z',
        recurrence: rule
      )

      expect(stub).to have_been_requested
    end
  end

  describe '#update' do
    it 'updates an event and returns the updated CalendarEvent' do
      updated = event_fixture.merge('title' => 'Updated Meeting')
      MockServer.stub_json(:put, '/event', updated)

      event = client.events.update(
        provider: 'google',
        calendar_id: 'primary',
        event_id: 'evt-1',
        title: 'Updated Meeting'
      )

      expect(event.title).to eq('Updated Meeting')
    end
  end

  describe '#delete' do
    it 'deletes an event and returns nil' do
      MockServer.stub_status(:delete, '/event', 204)

      result = client.events.delete(
        provider: 'google',
        calendar_id: 'primary',
        event_id: 'evt-1'
      )

      expect(result).to be_nil
    end

    it 'sends delete_mode and recurring_event_id' do
      stub = WebMock.stub_request(:delete, "#{MockServer::BASE_URL}/event")
                    .with(query: hash_including('deleteMode' => 'all', 'recurringEventId' => 'evt-series'))
                    .to_return(status: 204, body: '')

      client.events.delete(
        provider: 'google',
        calendar_id: 'primary',
        event_id: 'evt-instance',
        recurring_event_id: 'evt-series',
        delete_mode: 'all'
      )

      expect(stub).to have_been_requested
    end
  end
end
