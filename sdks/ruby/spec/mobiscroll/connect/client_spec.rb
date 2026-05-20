# frozen_string_literal: true

require 'spec_helper'
require 'support/mock_server'

RSpec.describe Mobiscroll::Connect::Client do
  let(:events_url) { "#{MockServer::BASE_URL}/events" }
  let(:token_url)  { "#{MockServer::BASE_URL}/oauth/token" }
  let(:json_header) { { 'Content-Type' => 'application/json' } }

  it 'constructs successfully with required params' do
    client = MockServer.default_client
    expect(client).to be_a(described_class)
  end

  it 'exposes auth, calendars, and events resources' do
    client = MockServer.default_client
    expect(client.auth).to be_a(Mobiscroll::Connect::Resources::Auth)
    expect(client.calendars).to be_a(Mobiscroll::Connect::Resources::Calendars)
    expect(client.events).to be_a(Mobiscroll::Connect::Resources::Events)
  end

  it 'raises on missing client_id' do
    expect {
      Mobiscroll::Connect::Client.new(
        client_id: '',
        client_secret: 'secret',
        redirect_uri: 'http://localhost/callback'
      )
    }.to raise_error(Mobiscroll::Connect::Error, /client_id/)
  end

  it 'raises on missing client_secret' do
    expect {
      Mobiscroll::Connect::Client.new(
        client_id: 'id',
        client_secret: nil,
        redirect_uri: 'http://localhost/callback'
      )
    }.to raise_error(Mobiscroll::Connect::Error, /client_secret/)
  end

  it 'stores and retrieves credentials' do
    client = MockServer.default_client
    tokens = Mobiscroll::Connect::TokenResponse.new(
      access_token: 'abc', token_type: 'Bearer', refresh_token: 'xyz'
    )
    client.set_credentials(tokens)
    expect(client.credentials.access_token).to eq('abc')
  end

  it 'fires on_tokens_refreshed callback when set at construction' do
    received = nil
    tokens = Mobiscroll::Connect::TokenResponse.new(
      access_token: 'initial', refresh_token: 'rt'
    )
    new_tokens = { 'access_token' => 'refreshed', 'token_type' => 'Bearer' }

    WebMock.stub_request(:get, events_url)
           .to_return(status: 401, body: '{"message":"unauthorized"}', headers: json_header)
           .then
           .to_return(status: 200, body: JSON.generate({ 'events' => [] }), headers: json_header)
    WebMock.stub_request(:post, token_url)
           .to_return(status: 200, body: JSON.generate(new_tokens), headers: json_header)

    client = MockServer.default_client(on_tokens_refreshed: ->(t) { received = t })
    client.set_credentials(tokens)
    client.events.list

    expect(received).not_to be_nil
    expect(received.access_token).to eq('refreshed')
  end

  it 'fires on_tokens_refreshed callback when set via method' do
    received = nil
    tokens = Mobiscroll::Connect::TokenResponse.new(
      access_token: 'initial', refresh_token: 'rt'
    )
    new_tokens = { 'access_token' => 'refreshed', 'token_type' => 'Bearer' }

    WebMock.stub_request(:get, events_url)
           .to_return(status: 401, body: '{"message":"unauthorized"}', headers: json_header)
           .then
           .to_return(status: 200, body: JSON.generate({ 'events' => [] }), headers: json_header)
    WebMock.stub_request(:post, token_url)
           .to_return(status: 200, body: JSON.generate(new_tokens), headers: json_header)

    client = MockServer.default_client
    client.set_credentials(tokens)
    client.on_tokens_refreshed { |t| received = t }
    client.events.list

    expect(received).not_to be_nil
  end
end
