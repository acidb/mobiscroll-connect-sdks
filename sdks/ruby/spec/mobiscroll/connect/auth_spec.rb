# frozen_string_literal: true

require 'spec_helper'
require 'support/mock_server'
require 'uri'

RSpec.describe Mobiscroll::Connect::Resources::Auth do
  let(:client) { MockServer.default_client }

  describe '#generate_auth_url' do
    it 'includes required query params' do
      url = client.auth.generate_auth_url(user_id: 'user-123')
      uri = URI.parse(url)
      params = URI.decode_www_form(uri.query).to_h

      expect(uri.path).to end_with('/oauth/authorize')
      expect(params['response_type']).to eq('code')
      expect(params['client_id']).to eq('test-client-id')
      expect(params['redirect_uri']).to eq('http://localhost:3000/callback')
      expect(params['user_id']).to eq('user-123')
    end

    it 'includes optional scope and state' do
      url = client.auth.generate_auth_url(
        user_id: 'user-123',
        scope: 'calendar.read',
        state: 'random-state'
      )
      params = URI.decode_www_form(URI.parse(url).query).to_h
      expect(params['scope']).to eq('calendar.read')
      expect(params['state']).to eq('random-state')
    end

    it 'includes the lng param when provided' do
      url = client.auth.generate_auth_url(user_id: 'user-123', lng: 'es')
      params = URI.decode_www_form(URI.parse(url).query).to_h
      expect(params['lng']).to eq('es')
    end

    it 'omits the lng param when not provided' do
      url = client.auth.generate_auth_url(user_id: 'user-123')
      params = URI.decode_www_form(URI.parse(url).query).to_h
      expect(params).not_to have_key('lng')
    end

    it 'includes multiple providers as repeated params' do
      url = client.auth.generate_auth_url(
        user_id: 'user-123',
        providers: [Mobiscroll::Connect::Provider::GOOGLE, Mobiscroll::Connect::Provider::MICROSOFT]
      )
      provider_values = URI.decode_www_form(URI.parse(url).query)
                           .select { |k, _| k == 'providers' } # rubocop:disable Style/HashSlice
                           .map(&:last)
      expect(provider_values).to contain_exactly('google', 'microsoft')
    end
  end

  describe '#get_token' do
    it 'exchanges code for tokens and stores credentials' do
      token_response = {
        'access_token' => 'test-access-token',
        'token_type' => 'Bearer',
        'expires_in' => 3600,
        'refresh_token' => 'test-refresh-token'
      }
      MockServer.stub_form('/oauth/token', token_response)

      tokens = client.auth.get_token('auth-code-123')

      expect(tokens).to be_a(Mobiscroll::Connect::TokenResponse)
      expect(tokens.access_token).to eq('test-access-token')
      expect(tokens.refresh_token).to eq('test-refresh-token')
      expect(client.credentials.access_token).to eq('test-access-token')
    end
  end

  describe '#get_connection_status' do
    it 'returns connection status from /oauth/connection-status' do
      MockServer.stub_json(:get, '/oauth/connection-status', {
                             'connections' => {
                               'google' => [{ 'id' => 'acc1', 'display' => 'user@gmail.com' }]
                             },
                             'limitReached' => false
                           })

      status = MockServer.client_with_tokens.auth.get_connection_status

      expect(status).to be_a(Mobiscroll::Connect::ConnectionStatus)
      expect(status.connections['google'].first.display).to eq('user@gmail.com')
      expect(status.limit_reached).to be false
    end

    it 'falls back to /connection-status on 404' do
      WebMock.stub_request(:get, "#{MockServer::BASE_URL}/oauth/connection-status")
             .to_return(status: 404, body: '{"message":"not found"}',
                        headers: { 'Content-Type' => 'application/json' })
      MockServer.stub_json(:get, '/connection-status', {
                             'connections' => { 'microsoft' => [] },
                             'limitReached' => false
                           })

      status = MockServer.client_with_tokens.auth.get_connection_status
      expect(status.connections.key?('microsoft')).to be true
    end
  end

  describe '#disconnect' do
    it 'disconnects a provider' do
      MockServer.stub_json(:post, '/oauth/disconnect', { 'success' => true })

      result = MockServer.client_with_tokens.auth.disconnect(
        provider: Mobiscroll::Connect::Provider::GOOGLE
      )
      expect(result.success).to be true
    end

    it 'falls back to /disconnect on 404' do
      WebMock.stub_request(:post, /\A#{Regexp.escape("#{MockServer::BASE_URL}/oauth/disconnect")}/)
             .to_return(status: 404, body: '{"message":"not found"}',
                        headers: { 'Content-Type' => 'application/json' })
      MockServer.stub_json(:post, '/disconnect', { 'success' => true })

      result = MockServer.client_with_tokens.auth.disconnect(
        provider: Mobiscroll::Connect::Provider::GOOGLE
      )
      expect(result.success).to be true
    end
  end
end
