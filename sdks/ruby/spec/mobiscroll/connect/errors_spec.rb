# frozen_string_literal: true

require 'spec_helper'
require 'support/mock_server'

RSpec.describe 'Error mapping' do
  # No refresh_token so 401 is surfaced immediately without triggering a refresh attempt
  let(:client) { MockServer.client_with_access_token_only }
  let(:events_url) { "#{MockServer::BASE_URL}/events" }

  shared_examples 'raises correct error' do |status, error_class|
    it "raises #{error_class} for HTTP #{status}" do
      WebMock.stub_request(:get, "#{MockServer::BASE_URL}/events")
             .to_return(
               status: status,
               body: JSON.generate({ 'message' => 'error message' }),
               headers: { 'Content-Type' => 'application/json' }
             )

      expect { client.events.list }.to raise_error(error_class)
    end
  end

  include_examples 'raises correct error', 401, Mobiscroll::Connect::AuthenticationError
  include_examples 'raises correct error', 403, Mobiscroll::Connect::AuthenticationError
  include_examples 'raises correct error', 404, Mobiscroll::Connect::NotFoundError
  include_examples 'raises correct error', 400, Mobiscroll::Connect::ValidationError
  include_examples 'raises correct error', 422, Mobiscroll::Connect::ValidationError
  include_examples 'raises correct error', 500, Mobiscroll::Connect::ServerError
  include_examples 'raises correct error', 503, Mobiscroll::Connect::ServerError

  it 'raises RateLimitError for 429 with retry_after' do
    WebMock.stub_request(:get, "#{MockServer::BASE_URL}/events")
           .to_return(
             status: 429,
             body: JSON.generate({ 'message' => 'rate limited' }),
             headers: { 'Content-Type' => 'application/json', 'Retry-After' => '60' }
           )

    expect { client.events.list }.to raise_error(Mobiscroll::Connect::RateLimitError) do |err|
      expect(err.retry_after).to eq(60)
    end
  end

  it 'extracts details from ValidationError' do
    WebMock.stub_request(:get, "#{MockServer::BASE_URL}/events")
           .to_return(
             status: 400,
             body: JSON.generate({ 'message' => 'bad input', 'details' => { 'field' => 'start' } }),
             headers: { 'Content-Type' => 'application/json' }
           )

    expect { client.events.list }.to raise_error(Mobiscroll::Connect::ValidationError) do |err|
      expect(err.details).to eq({ 'field' => 'start' })
    end
  end

  it 'sets status_code on ServerError' do
    WebMock.stub_request(:get, "#{MockServer::BASE_URL}/events")
           .to_return(
             status: 502,
             body: JSON.generate({ 'message' => 'bad gateway' }),
             headers: { 'Content-Type' => 'application/json' }
           )

    expect { client.events.list }.to raise_error(Mobiscroll::Connect::ServerError) do |err|
      expect(err.status_code).to eq(502)
    end
  end

  it 'raises NetworkError on connection failure' do
    WebMock.stub_request(:get, "#{MockServer::BASE_URL}/events")
           .to_raise(Faraday::ConnectionFailed.new('connection refused'))

    expect { client.events.list }.to raise_error(Mobiscroll::Connect::NetworkError)
  end

  it 'error classes are all subclasses of Mobiscroll::Connect::Error' do
    [
      Mobiscroll::Connect::AuthenticationError,
      Mobiscroll::Connect::NotFoundError,
      Mobiscroll::Connect::ValidationError,
      Mobiscroll::Connect::RateLimitError,
      Mobiscroll::Connect::ServerError,
      Mobiscroll::Connect::NetworkError
    ].each do |klass|
      expect(klass.ancestors).to include(Mobiscroll::Connect::Error)
    end
  end
end
