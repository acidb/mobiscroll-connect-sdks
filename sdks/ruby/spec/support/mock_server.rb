# frozen_string_literal: true

require 'json'

module MockServer
  BASE_URL = 'https://connect.mobiscroll.com/api'

  def self.stub_json(method, path, response_body, status: 200, headers: {})
    WebMock.stub_request(method, /\A#{Regexp.escape("#{BASE_URL}#{path}")}/)
           .to_return(
             status: status,
             body: JSON.generate(response_body),
             headers: { 'Content-Type' => 'application/json' }.merge(headers)
           )
  end

  def self.stub_status(method, path, status)
    WebMock.stub_request(method, /\A#{Regexp.escape("#{BASE_URL}#{path}")}/)
           .to_return(status: status, body: '', headers: {})
  end

  def self.stub_form(path, response_body, status: 200)
    WebMock.stub_request(:post, "#{BASE_URL}#{path}")
           .to_return(
             status: status,
             body: JSON.generate(response_body),
             headers: { 'Content-Type' => 'application/json' }
           )
  end

  def self.default_client(**)
    Mobiscroll::Connect::Client.new(
      client_id: 'test-client-id',
      client_secret: 'test-client-secret',
      redirect_uri: 'http://localhost:3000/callback',
      **
    )
  end

  def self.client_with_tokens(**)
    client = default_client(**)
    tokens = Mobiscroll::Connect::TokenResponse.new(
      access_token: 'test-access-token',
      token_type: 'Bearer',
      refresh_token: 'test-refresh-token'
    )
    client.set_credentials(tokens)
    client
  end

  # Client with only an access_token — no refresh_token, so 401 is not retried.
  def self.client_with_access_token_only(**)
    client = default_client(**)
    tokens = Mobiscroll::Connect::TokenResponse.new(
      access_token: 'test-access-token',
      token_type: 'Bearer'
    )
    client.set_credentials(tokens)
    client
  end
end
