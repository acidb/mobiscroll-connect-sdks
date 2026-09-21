# frozen_string_literal: true

require 'spec_helper'
require 'support/mock_server'
require 'json'

RSpec.describe Mobiscroll::Connect::Resources::Webhooks do
  let(:client) { MockServer.client_with_tokens }

  describe '#subscribe_webhook' do
    it 'subscribes and returns a SubscribeWebhookResponse' do
      MockServer.stub_json(:post, '/subscribe-webhook', {
                             'success' => true,
                             'provider' => 'google',
                             'subscription' => {
                               'channelId' => 'chan-1',
                               'resourceId' => 'res-1',
                               'expiration' => '2024-03-01T00:00:00Z'
                             },
                             'serverWebhookUrl' => 'https://connect.example.com/webhook',
                             'channelId' => 'chan-1'
                           })

      result = client.webhooks.subscribe_webhook(
        provider: 'google',
        calendar_id: 'primary'
      )

      expect(result).to be_a(Mobiscroll::Connect::SubscribeWebhookResponse)
      expect(result.success).to be(true)
      expect(result.provider).to eq('google')
      expect(result.channel_id).to eq('chan-1')
      expect(result.server_webhook_url).to eq('https://connect.example.com/webhook')
      expect(result.subscription).to be_a(Mobiscroll::Connect::WebhookSubscription)
      expect(result.subscription.channel_id).to eq('chan-1')
      expect(result.subscription.resource_id).to eq('res-1')
      expect(result.subscription.expiration).to eq('2024-03-01T00:00:00Z')
    end

    it 'sends optional channel_id and expiration in the request body' do
      expected_body = {
        'provider' => 'google',
        'calendarId' => 'primary',
        'channelId' => 'my-channel',
        'expiration' => 1_700_000_000_000
      }
      response_body = {
        'success' => true,
        'provider' => 'google',
        'subscription' => { 'channelId' => 'my-channel' },
        'serverWebhookUrl' => 'https://x',
        'channelId' => 'my-channel'
      }
      stub = WebMock.stub_request(:post, "#{MockServer::BASE_URL}/subscribe-webhook")
                    .with { |req| JSON.parse(req.body) == expected_body }
                    .to_return(
                      status: 200,
                      body: JSON.generate(response_body),
                      headers: { 'Content-Type' => 'application/json' }
                    )

      client.webhooks.subscribe_webhook(
        provider: 'google',
        calendar_id: 'primary',
        channel_id: 'my-channel',
        expiration: 1_700_000_000_000
      )

      expect(stub).to have_been_requested
    end

    it 'raises ValidationError for a 400 (missing provider/calendarId)' do
      MockServer.stub_json(:post, '/subscribe-webhook', { 'message' => 'calendarId is required' }, status: 400)

      expect do
        client.webhooks.subscribe_webhook(provider: 'google', calendar_id: 'primary')
      end.to raise_error(Mobiscroll::Connect::ValidationError, 'calendarId is required')
    end

    it 'raises AuthenticationError for a 401' do
      MockServer.stub_json(:post, '/subscribe-webhook', { 'message' => 'unauthorized' }, status: 401)

      expect do
        MockServer.client_with_access_token_only.webhooks.subscribe_webhook(
          provider: 'google',
          calendar_id: 'primary'
        )
      end.to raise_error(Mobiscroll::Connect::AuthenticationError)
    end

    it 'raises ServerError for a 500' do
      MockServer.stub_json(:post, '/subscribe-webhook', { 'message' => 'boom' }, status: 500)

      expect do
        client.webhooks.subscribe_webhook(provider: 'google', calendar_id: 'primary')
      end.to raise_error(Mobiscroll::Connect::ServerError)
    end
  end

  describe '#unsubscribe_webhook' do
    it 'unsubscribes and returns an UnsubscribeWebhookResponse' do
      MockServer.stub_json(:post, '/unsubscribe-webhook', { 'success' => true })

      result = client.webhooks.unsubscribe_webhook(provider: 'google', channel_id: 'chan-1', resource_id: 'res-1')

      expect(result).to be_a(Mobiscroll::Connect::UnsubscribeWebhookResponse)
      expect(result.success).to be(true)
      expect(result.message).to be_nil
    end

    it 'sends resource_id in the request body when given' do
      expected_body = { 'provider' => 'google', 'channelId' => 'chan-1', 'resourceId' => 'res-1' }
      stub = WebMock.stub_request(:post, "#{MockServer::BASE_URL}/unsubscribe-webhook")
                    .with { |req| JSON.parse(req.body) == expected_body }
                    .to_return(status: 200, body: JSON.generate({ 'success' => true }),
                               headers: { 'Content-Type' => 'application/json' })

      client.webhooks.unsubscribe_webhook(provider: 'google', channel_id: 'chan-1', resource_id: 'res-1')

      expect(stub).to have_been_requested
    end

    it 'returns success: true with an explanatory message on a provider-side failure' do
      MockServer.stub_json(:post, '/unsubscribe-webhook', {
                             'success' => true,
                             'message' => 'Subscription already expired'
                           })

      result = client.webhooks.unsubscribe_webhook(provider: 'google', channel_id: 'chan-1')

      expect(result.success).to be(true)
      expect(result.message).to eq('Subscription already expired')
    end

    it 'raises ValidationError for a 400 (missing provider/channelId)' do
      MockServer.stub_json(:post, '/unsubscribe-webhook', { 'message' => 'channelId is required' }, status: 400)

      expect do
        client.webhooks.unsubscribe_webhook(provider: 'google', channel_id: 'chan-1')
      end.to raise_error(Mobiscroll::Connect::ValidationError, 'channelId is required')
    end

    it 'raises AuthenticationError for a 401' do
      MockServer.stub_json(:post, '/unsubscribe-webhook', { 'message' => 'unauthorized' }, status: 401)

      expect do
        MockServer.client_with_access_token_only.webhooks.unsubscribe_webhook(
          provider: 'google',
          channel_id: 'chan-1'
        )
      end.to raise_error(Mobiscroll::Connect::AuthenticationError)
    end

    it 'raises ServerError for a 500' do
      MockServer.stub_json(:post, '/unsubscribe-webhook', { 'message' => 'boom' }, status: 500)

      expect do
        client.webhooks.unsubscribe_webhook(provider: 'google', channel_id: 'chan-1')
      end.to raise_error(Mobiscroll::Connect::ServerError)
    end
  end
end
