# frozen_string_literal: true

require 'spec_helper'
require 'support/mock_server'
require 'json'

RSpec.describe 'Token refresh deduplication' do
  let(:events_url)     { "#{MockServer::BASE_URL}/events" }
  let(:token_url)      { "#{MockServer::BASE_URL}/oauth/token" }
  let(:json_header)    { { 'Content-Type' => 'application/json' } }
  let(:event_body)     { JSON.generate({ 'events' => [], 'pageSize' => 50 }) }
  let(:new_tokens_body) do
    { 'access_token' => 'new-token', 'token_type' => 'Bearer', 'refresh_token' => 'refresh-token' }
  end

  it 'issues exactly one POST /oauth/token for concurrent 401s' do
    n_threads = 5

    # All GET /events return 401 (no limit — each thread will hit one)
    WebMock.stub_request(:get, events_url)
           .to_return(
             status: 401,
             body: JSON.generate({ 'message' => 'unauthorized' }),
             headers: json_header
           ).then
           .to_return(status: 200, body: event_body, headers: json_header)

    token_stub = WebMock.stub_request(:post, token_url)
                        .to_return(
                          status: 200,
                          body: JSON.generate(new_tokens_body),
                          headers: json_header
                        )

    tokens = Mobiscroll::Connect::TokenResponse.new(
      access_token: 'old-token',
      refresh_token: 'refresh-token'
    )
    client = MockServer.default_client
    client.set_credentials(tokens)

    errors = []
    threads = n_threads.times.map do
      Thread.new do
        client.events.list
      rescue StandardError => e
        errors << e
      end
    end
    threads.each(&:join)

    expect(errors).to be_empty
    expect(token_stub).to have_been_requested.at_most_once
  end

  it 'preserves existing refresh_token when server omits it from refresh response' do
    tokens = Mobiscroll::Connect::TokenResponse.new(
      access_token: 'old-token',
      refresh_token: 'original-refresh-token'
    )
    # Server returns no refresh_token in the refresh response
    WebMock.stub_request(:get, events_url)
           .to_return(
             status: 401,
             body: JSON.generate({ 'message' => 'unauthorized' }),
             headers: json_header
           ).then
           .to_return(status: 200, body: event_body, headers: json_header)

    WebMock.stub_request(:post, token_url)
           .to_return(
             status: 200,
             body: JSON.generate({ 'access_token' => 'new-access-token', 'token_type' => 'Bearer' }),
             headers: json_header
           )

    client = MockServer.default_client
    client.set_credentials(tokens)
    client.events.list

    expect(client.credentials.access_token).to eq('new-access-token')
    expect(client.credentials.refresh_token).to eq('original-refresh-token')
  end
end
