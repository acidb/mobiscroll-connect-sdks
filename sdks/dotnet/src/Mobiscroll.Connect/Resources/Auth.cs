using System;
using System.Collections.Generic;
using System.Net;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using Mobiscroll.Connect.Exceptions;
using Mobiscroll.Connect.Internal;
using Mobiscroll.Connect.Models;

namespace Mobiscroll.Connect.Resources;

public sealed class Auth
{
    private readonly ApiClient _client;

    internal Auth(ApiClient client)
    {
        _client = client;
    }

    /// <summary>
    /// Build the OAuth2 authorization URL to redirect the user to.
    /// </summary>
    public string GenerateAuthUrl(AuthorizeParams authParams)
    {
        if (authParams is null)
        {
            throw new ArgumentNullException(nameof(authParams));
        }
        if (string.IsNullOrEmpty(authParams.UserId))
        {
            throw new ArgumentException("UserId is required", nameof(authParams));
        }

        var cfg = _client.Config;
        var qs = new QueryStringBuilder()
            .Add("response_type", "code")
            .Add("client_id", cfg.ClientId)
            .Add("user_id", authParams.UserId)
            .Add("redirect_uri", cfg.RedirectUri)
            .Add("state", authParams.State)
            .Add("scope", authParams.Scope)
            .Add("providers", authParams.Providers)
            .Add("lng", authParams.Lng)
            .Add("theme", authParams.Theme);

        // BaseUrl ends with a slash — keep the resulting URL clean.
        return $"{_client.BaseUrl}oauth/authorize?{qs}";
    }

    /// <summary>
    /// Exchange an authorization code for an access token.
    /// On success, credentials are set on the client automatically.
    /// </summary>
    public async Task<TokenResponse> GetTokenAsync(string code, CancellationToken ct = default)
    {
        if (string.IsNullOrEmpty(code))
        {
            throw new ArgumentException("code is required", nameof(code));
        }

        var cfg = _client.Config;
        var basic = Convert.ToBase64String(Encoding.UTF8.GetBytes($"{cfg.ClientId}:{cfg.ClientSecret}"));

        var form = new List<KeyValuePair<string, string>>
        {
            new("grant_type", "authorization_code"),
            new("code", code),
            new("redirect_uri", cfg.RedirectUri),
        };

        var headers = new Dictionary<string, string>
        {
            ["Authorization"] = $"Basic {basic}",
            ["CLIENT_ID"] = cfg.ClientId,
        };

        TokenResponse? tokens;
        try
        {
            tokens = await _client.PostFormNoRefreshAsync<TokenResponse>("/oauth/token", form, headers, ct)
                .ConfigureAwait(false);
        }
        catch
        {
            throw new MobiscrollConnectException(
                "Error exchanging code for token. Please check your client ID, client secret, and redirect URI configuration and try again.");
        }

        if (tokens is null || string.IsNullOrEmpty(tokens.AccessToken))
        {
            throw new MobiscrollConnectException(
                "Error exchanging code for token. Please check your client ID, client secret, and redirect URI configuration and try again.");
        }

        _client.SetCredentials(tokens);
        return tokens;
    }

    /// <summary>Set credentials directly (e.g. after loading from persistence).</summary>
    public void SetCredentials(TokenResponse tokens) => _client.SetCredentials(tokens);

    /// <summary>
    /// Get the connection status across all providers for the authenticated user.
    /// Falls back to the legacy <c>/connection-status</c> path when the primary
    /// <c>/oauth/connection-status</c> path is unavailable.
    /// </summary>
    public async Task<ConnectionStatusResponse> GetConnectionStatusAsync(CancellationToken ct = default)
    {
        try
        {
            var resp = await _client.GetAsync<ConnectionStatusResponse>("/oauth/connection-status", ct)
                .ConfigureAwait(false);
            return resp ?? new ConnectionStatusResponse();
        }
        catch (MobiscrollConnectException)
        {
            var resp = await _client.GetAsync<ConnectionStatusResponse>("/connection-status", ct)
                .ConfigureAwait(false);
            return resp ?? new ConnectionStatusResponse();
        }
    }

    /// <summary>
    /// Disconnect a calendar provider account.
    /// </summary>
    /// <remarks>
    /// Removes the account (or, when <c>Account</c> is null, every account of the provider) from the
    /// user: stops its webhook subscriptions and deletes the stored provider credentials. It does not
    /// revoke the user's Connect access or refresh tokens (see <see cref="RevokeAsync"/>), nor the
    /// grant at Google or Microsoft. <c>Account</c> is the <c>Id</c> of an account from
    /// <see cref="GetConnectionStatusAsync"/>.
    /// </remarks>
    /// <example>
    /// <code>
    /// await client.Auth.DisconnectAsync(new DisconnectParams { Provider = Provider.Google, Account = "user@gmail.com" });
    /// await client.Auth.DisconnectAsync(new DisconnectParams { Provider = Provider.Microsoft });
    /// </code>
    /// </example>
    public async Task<DisconnectResponse> DisconnectAsync(DisconnectParams disconnectParams, CancellationToken ct = default)
    {
        if (disconnectParams is null)
        {
            throw new ArgumentNullException(nameof(disconnectParams));
        }

        var qs = new QueryStringBuilder()
            .Add("provider", disconnectParams.Provider.ToWireString())
            .Add("account", string.IsNullOrEmpty(disconnectParams.Account) ? null : disconnectParams.Account);

        try
        {
            var resp = await _client.PostJsonAsync<DisconnectResponse>($"/oauth/disconnect?{qs}", new { }, ct)
                .ConfigureAwait(false);
            return resp ?? new DisconnectResponse { Success = true };
        }
        catch (MobiscrollConnectException)
        {
            var resp = await _client.PostJsonAsync<DisconnectResponse>($"/disconnect?{qs}", new { }, ct)
                .ConfigureAwait(false);
            return resp ?? new DisconnectResponse { Success = true };
        }
    }

    /// <summary>
    /// Revoke the user's Connect access and refresh tokens.
    /// </summary>
    /// <remarks>
    /// Revokes the whole grant: every access and refresh token issued for this user. It does not
    /// disconnect calendar accounts or stop webhook subscriptions. To remove a user entirely, call
    /// <see cref="DisconnectAsync"/> for each provider first (it needs a valid access token), then
    /// <see cref="RevokeAsync"/>.
    /// </remarks>
    /// <param name="token">
    /// Access or refresh token to revoke. When null, the stored refresh token (else the stored access
    /// token) is revoked and the stored credentials are cleared.
    /// </param>
    /// <param name="ct">Cancellation token.</param>
    /// <returns><c>Success = true</c>, also for an unknown or already revoked token.</returns>
    /// <exception cref="ValidationException">No token was given and none is stored.</exception>
    /// <example>
    /// <code>
    /// await client.Auth.DisconnectAsync(new DisconnectParams { Provider = Provider.Google });
    /// await client.Auth.RevokeAsync();
    ///
    /// // Or revoke a token you stored yourself
    /// await client.Auth.RevokeAsync(storedRefreshToken);
    /// </code>
    /// </example>
    public async Task<RevokeResponse> RevokeAsync(string? token = null, CancellationToken ct = default)
    {
        var credentials = _client.Credentials;
        var revokeToken = token
            ?? (string.IsNullOrEmpty(credentials?.RefreshToken) ? credentials?.AccessToken : credentials.RefreshToken);
        if (string.IsNullOrEmpty(revokeToken))
        {
            throw new ValidationException("No token to revoke: pass one or set credentials first");
        }

        var resp = await _client.PostJsonNoRefreshAsync<RevokeResponse>("/oauth/revoke", new { token = revokeToken }, ct)
            .ConfigureAwait(false);

        if (token is null)
        {
            _client.ClearCredentials();
        }

        return resp ?? new RevokeResponse { Success = true };
    }
}
