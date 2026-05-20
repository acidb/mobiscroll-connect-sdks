# frozen_string_literal: true

require 'spec_helper'
require 'support/mock_server'

RSpec.describe Mobiscroll::Connect::Resources::Calendars do
  let(:client) { MockServer.client_with_tokens }

  describe '#list' do
    it 'returns an array of Calendar objects' do
      MockServer.stub_json(:get, '/calendars', [
        {
          'provider' => 'google',
          'id' => 'primary',
          'title' => 'My Calendar',
          'timeZone' => 'America/New_York',
          'color' => '#4285F4'
        },
        {
          'provider' => 'microsoft',
          'id' => 'cal-2',
          'title' => 'Work Calendar',
          'timeZone' => 'UTC',
          'color' => nil
        }
      ])

      calendars = client.calendars.list

      expect(calendars).to be_an(Array)
      expect(calendars.length).to eq(2)
      expect(calendars.first).to be_a(Mobiscroll::Connect::Calendar)
      expect(calendars.first.provider).to eq('google')
      expect(calendars.first.id).to eq('primary')
      expect(calendars.first.title).to eq('My Calendar')
      expect(calendars.first.time_zone).to eq('America/New_York')
    end

    it 'returns empty array when API returns empty' do
      MockServer.stub_json(:get, '/calendars', [])
      expect(client.calendars.list).to eq([])
    end
  end
end
