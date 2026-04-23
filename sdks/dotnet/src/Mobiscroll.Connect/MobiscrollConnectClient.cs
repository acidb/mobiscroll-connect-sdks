using System;
using System.Net.Http;
using Mobiscroll.Connect.Models;
using Mobiscroll.Connect.Resources;

namespace Mobiscroll.Connect;

/// <summary>
/// Top-level entry point for the Mobiscroll Connect SDK.
/// Construct once per application (or per user session, if credentials are user-scoped)
/// and access the <see cref="Auth"/>, <see cref="Calendars"/>, and <see cref="Events"/>
/// resources as properties.
/// </summary>
public sealed class MobiscrollConnectClient : IDisposable
{
    private readonly ApiClient _api;

    public MobiscrollConnectClient(string clientId, string clientSecret, string redirectUri)
        : this(new MobiscrollConnectConfig
        {
            ClientId = clientId,
            ClientSecret = clientSecret,
            RedirectUri = redirectUri,
        })
    {
    }

    public MobiscrollConnectClient(MobiscrollConnectConfig config, HttpClient? httpClient = null, string? baseUrl = null)
    {
        _api = new ApiClient(config, httpClient, baseUrl);
        Auth = new Auth(_api);
        Calendars = new Calendars(_api);
        Events = new Events(_api);
    }

    public Auth Auth { get; }
    public Calendars Calendars { get; }
    public Events Events { get; }

    /// <summary>Underlying API client. Exposed for advanced scenarios (custom requests, test access).</summary>
    public ApiClient ApiClient => _api;

    /// <summary>Set OAuth credentials (access + refresh tokens) for subsequent requests.</summary>
    public void SetCredentials(TokenResponse tokens) => _api.SetCredentials(tokens);

    /// <summary>Register a callback that fires whenever the SDK silently refreshes the access token.</summary>
    public void OnTokensRefreshed(Action<TokenResponse> callback) => _api.OnTokensRefreshed(callback);

    public void Dispose() => _api.Dispose();
}
