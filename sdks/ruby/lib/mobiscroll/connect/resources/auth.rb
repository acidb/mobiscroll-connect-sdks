# frozen_string_literal: true

require 'uri'

module Mobiscroll
  module Connect
    module Resources
      class Auth
        def initialize(config, api_client)
          @config = config
          @api_client = api_client
        end

        def generate_auth_url(user_id:, scope: nil, state: nil, providers: nil)
          params = {
            'response_type' => 'code',
            'client_id' => @config.client_id,
            'redirect_uri' => @config.redirect_uri,
            'user_id' => user_id
          }
          params['scope'] = scope if scope
          params['state'] = state if state

          query = URI.encode_www_form(params)

          if providers && !providers.empty?
            provider_params = providers.map { |p| "providers=#{URI.encode_www_form_component(p)}" }.join('&')
            query = "#{query}&#{provider_params}"
          end

          "#{@config.base_url}/oauth/authorize?#{query}"
        end

        def get_token(code)
          form = {
            'grant_type' => 'authorization_code',
            'code' => code,
            'redirect_uri' => @config.redirect_uri
          }
          parsed = @api_client.post_form('/oauth/token', form)
          tokens = TokenResponse.from_h(parsed)
          @api_client.set_credentials(tokens)
          tokens
        end

        def set_credentials(tokens)
          @api_client.set_credentials(tokens)
        end

        def get_connection_status
          begin
            parsed = @api_client.get('/oauth/connection-status')
          rescue NotFoundError
            parsed = @api_client.get('/connection-status')
          end
          ConnectionStatus.from_h(parsed)
        end

        def disconnect(provider:, account: nil)
          query = { 'provider' => provider }
          query['account'] = account if account

          begin
            parsed = @api_client.post('/oauth/disconnect', query: query, body: {})
          rescue NotFoundError
            parsed = @api_client.post('/disconnect', query: query, body: {})
          end
          DisconnectResponse.from_h(parsed)
        end
      end
    end
  end
end
