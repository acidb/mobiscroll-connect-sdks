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

        # Builds the consent URL to redirect the user to. `lng` is the Connect page language
        # (e.g. 'es'); `theme` is the Connect page color scheme: 'light', 'dark' or 'auto'
        # (follows the device setting), the project's default theme when nil.
        def generate_auth_url(user_id:, scope: nil, state: nil, providers: nil, lng: nil, theme: nil)
          params = {
            'response_type' => 'code',
            'client_id' => @config.client_id,
            'redirect_uri' => @config.redirect_uri,
            'user_id' => user_id
          }
          params['scope'] = scope if scope
          params['state'] = state if state
          params['lng'] = lng if lng
          params['theme'] = theme if theme

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

        # Disconnects a calendar provider account.
        #
        # Removes the account (or, without +account+, every account of the provider)
        # from the user: stops its webhook subscriptions and deletes the stored
        # provider credentials. It does not revoke the user's Connect access or
        # refresh tokens (see #revoke), nor the grant at Google or Microsoft.
        #
        # @param provider [String] a Provider constant
        # @param account [String, nil] the +id+ of an account from #get_connection_status
        #   (<tt>connections[provider][].id</tt>)
        # @return [DisconnectResponse]
        #
        # @example
        #   client.auth.disconnect(provider: Mobiscroll::Connect::Provider::GOOGLE, account: 'user@gmail.com')
        #   client.auth.disconnect(provider: Mobiscroll::Connect::Provider::MICROSOFT)
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

        # Revokes the user's Connect access and refresh tokens.
        #
        # Revokes the whole grant: every access and refresh token issued for this
        # user. It does not disconnect calendar accounts or stop webhook
        # subscriptions. To remove a user entirely, call #disconnect for each
        # provider first (it needs a valid access token), then #revoke.
        #
        # @param token [String, nil] access or refresh token to revoke. Defaults to
        #   the stored refresh token, else the stored access token; the stored
        #   credentials are then cleared.
        # @return [RevokeResponse] success is true, also for an unknown or already
        #   revoked token
        # @raise [ValidationError] when no token is given and none is stored
        #
        # @example
        #   client.auth.disconnect(provider: Mobiscroll::Connect::Provider::GOOGLE)
        #   client.auth.revoke
        #
        #   # Or revoke a token you stored yourself
        #   client.auth.revoke(stored_refresh_token)
        def revoke(token = nil)
          stored = @api_client.credentials
          revoke_token = token || [stored&.refresh_token, stored&.access_token].find { |t| t && !t.empty? }
          if revoke_token.nil? || revoke_token.empty?
            raise ValidationError, 'No token to revoke: pass one or set credentials first'
          end

          parsed = @api_client.post('/oauth/revoke', body: { 'token' => revoke_token }, skip_refresh: true)
          @api_client.clear_credentials if token.nil?
          RevokeResponse.from_h(parsed)
        end
      end
    end
  end
end
