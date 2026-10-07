# frozen_string_literal: true

require 'spec_helper'
require 'support/mock_server'
require 'json'
require 'openssl'

WEBHOOK_VECTORS = JSON.parse(File.read(File.expand_path('../../fixtures/webhook-vectors.json', __dir__)))

RSpec.describe 'Webhook verification' do
  def vector(name)
    WEBHOOK_VECTORS['cases'].find { |c| c['name'] == name } or raise "Missing vector: #{name}"
  end

  let(:valid) { vector('valid single signature') }
  let(:keys) { WEBHOOK_VECTORS['keys'] }

  describe 'Mobiscroll::Connect.verify_webhook_signature' do
    WEBHOOK_VECTORS['cases'].each do |c|
      it c['name'] do
        run = lambda do
          Mobiscroll::Connect.verify_webhook_signature(c['body'], c['headers'], c['publicKeys'], now: c['now'])
        end
        if c['valid']
          expect(run.call).to be_nil
        else
          expect(&run).to raise_error(Mobiscroll::Connect::WebhookVerificationError)
        end
      end
    end

    it 'accepts a binary body and header names in any casing, with Array values' do
      headers = valid['headers'].to_h { |k, v| [k.upcase, [v]] }
      expect do
        Mobiscroll::Connect.verify_webhook_signature(valid['body'].b, headers, valid['publicKeys'], now: valid['now'])
      end.not_to raise_error
    end

    it 'accepts a Rack env with HTTP_ keys' do
      env = valid['headers'].to_h { |k, v| ["HTTP_#{k.upcase.tr('-', '_')}", v] }
      env['rack.input'] = StringIO.new(valid['body'])
      expect do
        Mobiscroll::Connect.verify_webhook_signature(valid['body'], env, valid['publicKeys'], now: valid['now'])
      end.not_to raise_error
    end

    it 'rejects a parsed body with a clear reason' do
      expect do
        Mobiscroll::Connect.verify_webhook_signature(
          JSON.parse(valid['body']), valid['headers'], valid['publicKeys'], now: valid['now']
        )
      end.to raise_error(Mobiscroll::Connect::WebhookVerificationError) { |e| expect(e.reason).to eq('invalid_payload') }
    end

    it 'reports why verification failed' do
      stale = vector('timestamp 301 s old, stale')
      expect do
        Mobiscroll::Connect.verify_webhook_signature(stale['body'], stale['headers'], stale['publicKeys'],
                                                     now: stale['now'])
      end.to raise_error(Mobiscroll::Connect::WebhookVerificationError) { |e|
        expect(e.reason).to eq('timestamp_out_of_tolerance')
        expect(e.code).to eq('WEBHOOK_VERIFICATION_ERROR')
        expect(e).to be_a(Mobiscroll::Connect::Error)
      }
    end

    it 'honours a custom tolerance' do
      stale = vector('timestamp 301 s old, stale')
      expect do
        Mobiscroll::Connect.verify_webhook_signature(stale['body'], stale['headers'], stale['publicKeys'],
                                                     now: stale['now'], tolerance_seconds: 301)
      end.not_to raise_error
    end
  end

  describe 'Webhooks#verify_webhook' do
    keys_url = 'https://connect.mobiscroll.com/.well-known/webhook-keys'

    let(:clock) { [valid['now'].to_f] }

    def keys_response(keys, cache_control = 'public, max-age=3600')
      entries = keys.map { |k| { 'id' => k, 'alg' => 'ed25519', 'key' => k, 'status' => 'active' } }
      {
        status: 200,
        body: JSON.generate({ 'keys' => entries }),
        headers: { 'Content-Type' => 'application/json', 'Cache-Control' => cache_control }
      }
    end

    def create_client(webhook_public_key = nil)
      MockServer.default_client(webhook_public_key: webhook_public_key)
    end

    def reason_of
      yield
      nil
    rescue Mobiscroll::Connect::WebhookVerificationError => e
      e.reason
    end

    before do
      Mobiscroll::Connect::WebhookKeyStore.reset!
      allow(Time).to receive(:now) { Time.at(clock[0]) }
    end

    it 'fetches the keys lazily from the origin of the API base URL and returns the parsed delivery' do
      stub = WebMock.stub_request(:get, keys_url).to_return(keys_response([keys['active']]))
      client = create_client
      expect(stub).not_to have_been_requested

      delivery = client.webhooks.verify_webhook(valid['body'], valid['headers'])

      expect(stub).to have_been_requested.once
      expect(delivery).to be_a(Mobiscroll::Connect::WebhookDelivery)
      expect(delivery.user_id).to eq('user_42')
      expect(delivery.provider).to eq('google')
      expect(delivery.events.first).to be_a(Mobiscroll::Connect::WebhookEvent)
      expect(delivery.events.first.title).to eq('Café meeting ☕ — Zoë')
      expect(delivery.events.first.last_modified).to eq('2026-09-21T10:00:00.000Z')
    end

    it 'derives the keys URL from a custom base URL' do
      stub = WebMock.stub_request(:get, 'https://connect-dev.example.com/.well-known/webhook-keys')
                    .to_return(keys_response([keys['active']]))
      client = MockServer.default_client(base_url: 'https://connect-dev.example.com/api')

      client.webhooks.verify_webhook(valid['body'], valid['headers'])

      expect(stub).to have_been_requested.once
    end

    it 'does not send the client credentials to the keys endpoint' do
      stub = WebMock.stub_request(:get, keys_url).to_return(keys_response([keys['active']]))
      MockServer.client_with_tokens.webhooks.verify_webhook(valid['body'], valid['headers'])

      expect(stub.with { |req| !req.headers.key?('Authorization') }).to have_been_requested.once
    end

    it 'shares one key cache across client instances and honours max-age' do
      stub = WebMock.stub_request(:get, keys_url).to_return(keys_response([keys['active']], 'max-age=120'))

      create_client.webhooks.verify_webhook(valid['body'], valid['headers'])
      create_client.webhooks.verify_webhook(valid['body'], valid['headers'])
      expect(stub).to have_been_requested.once

      clock[0] += 121
      create_client.webhooks.verify_webhook(valid['body'], valid['headers'])
      expect(stub).to have_been_requested.twice
    end

    it 're-fetches once after a rotation and accepts the delivery' do
      stub = WebMock.stub_request(:get, keys_url)
                    .to_return(keys_response([keys['unrelated']]))
                    .then.to_return(keys_response([keys['active']]))
      clock[0] -= 61
      Mobiscroll::Connect::WebhookKeyStore.for_url(keys_url).keys
      clock[0] += 61

      delivery = create_client.webhooks.verify_webhook(valid['body'], valid['headers'])

      expect(delivery.calendar_id).to eq('primary')
      expect(stub).to have_been_requested.twice
    end

    it 'does not re-fetch more than once a minute on forged deliveries' do
      stub = WebMock.stub_request(:get, keys_url).to_return(keys_response([keys['active']]))
      client = create_client
      forged = vector('tampered body')

      2.times do
        expect(reason_of { client.webhooks.verify_webhook(forged['body'], forged['headers']) })
          .to eq('no_matching_signature')
      end
      expect(stub).to have_been_requested.once
    end

    it 'falls back to the pinned key when the endpoint cannot be reached' do
      WebMock.stub_request(:get, keys_url).to_raise(Faraday::ConnectionFailed.new('ECONNREFUSED'))

      delivery = create_client(keys['active']).webhooks.verify_webhook(valid['body'], valid['headers'])

      expect(delivery.provider).to eq('google')
    end

    it 'falls back to the pinned key when the endpoint returns an error' do
      WebMock.stub_request(:get, keys_url).to_return(status: 503, body: '')

      delivery = create_client(keys['active']).webhooks.verify_webhook(valid['body'], valid['headers'])

      expect(delivery.user_id).to eq('user_42')
    end

    it 'ignores the pinned key while fetched keys are available' do
      WebMock.stub_request(:get, keys_url).to_return(keys_response([keys['unrelated']]))

      expect(reason_of { create_client(keys['active']).webhooks.verify_webhook(valid['body'], valid['headers']) })
        .to eq('no_matching_signature')
    end

    it 'keeps the last good keys when a refresh fails' do
      stub = WebMock.stub_request(:get, keys_url)
                    .to_return(keys_response([keys['active']]))
                    .then.to_timeout
      client = create_client
      client.webhooks.verify_webhook(valid['body'], valid['headers'])

      clock[0] += 3601
      later = valid['headers'].merge('webhook-timestamp' => (valid['now'] + 3601).to_s)
      expect(reason_of { client.webhooks.verify_webhook(valid['body'], later) }).to eq('no_matching_signature')
      expect(stub).to have_been_requested.twice
    end

    it 'ignores keys with another algorithm or without the whpk_ prefix' do
      body = JSON.generate({ 'keys' => [
                             { 'alg' => 'rsa', 'key' => keys['active'] },
                             { 'alg' => 'ed25519', 'key' => keys['active'].delete_prefix('whpk_') }
                           ] })
      WebMock.stub_request(:get, keys_url).to_return(status: 200, body: body)

      expect(reason_of { create_client.webhooks.verify_webhook(valid['body'], valid['headers']) })
        .to eq('no_public_keys')
    end

    it 'fails with no_public_keys when nothing can be fetched and no key is pinned' do
      WebMock.stub_request(:get, keys_url).to_raise(Faraday::ConnectionFailed.new('ECONNREFUSED'))

      expect(reason_of { create_client.webhooks.verify_webhook(valid['body'], valid['headers']) })
        .to eq('no_public_keys')
    end

    it 'raises other failures without a re-fetch' do
      stub = WebMock.stub_request(:get, keys_url).to_return(keys_response([keys['active']]))
      clock[0] -= 61
      Mobiscroll::Connect::WebhookKeyStore.for_url(keys_url).keys
      clock[0] += 61

      expect(reason_of { create_client.webhooks.verify_webhook(valid['body'], {}) }).to eq('missing_headers')
      expired = valid['headers'].merge('webhook-timestamp' => (valid['now'] - 301).to_s)
      expect(reason_of { create_client.webhooks.verify_webhook(valid['body'], expired) })
        .to eq('timestamp_out_of_tolerance')
      expect(stub).to have_been_requested.once
    end

    it 'shares one in-flight fetch between concurrent callers' do
      stub = WebMock.stub_request(:get, keys_url).to_return do
        sleep 0.2
        keys_response([keys['active']])
      end
      client = create_client

      results = Array.new(5) do
        Thread.new { client.webhooks.verify_webhook(valid['body'], valid['headers']) }
      end.map(&:value)

      expect(results.map(&:user_id)).to all(eq('user_42'))
      expect(stub).to have_been_requested.once
    end

    it 'accepts lower- and upper-case header names' do
      WebMock.stub_request(:get, keys_url).to_return(keys_response([keys['active']]))
      headers = valid['headers'].to_h { |k, v| [k.split('-').map(&:capitalize).join('-'), v] }

      expect(create_client.webhooks.verify_webhook(valid['body'], headers).user_id).to eq('user_42')
    end

    it 'rejects a correctly signed body that is not a JSON object' do
      signing_key = OpenSSL::PKey.generate_key('ED25519')
      public_key = "whpk_#{[signing_key.public_to_der[-32..]].pack('m0')}"
      id = 'msg_1'
      timestamp = valid['now'].to_s
      body = 'not json'
      signature = [signing_key.sign(nil, "#{id}.#{timestamp}.#{body}")].pack('m0')
      headers = { 'webhook-id' => id, 'webhook-timestamp' => timestamp, 'webhook-signature' => "v1a,#{signature}" }
      WebMock.stub_request(:get, keys_url).to_return(keys_response([public_key]))

      expect(reason_of { create_client.webhooks.verify_webhook(body, headers) }).to eq('invalid_payload')
    end
  end
end
