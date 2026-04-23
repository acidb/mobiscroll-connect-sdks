using System;
using System.Collections.Generic;
using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using Mobiscroll.Connect.Exceptions;
using Mobiscroll.Connect.Models;

namespace Mobiscroll.Connect;

public sealed class ApiClient : IDisposable
{
    internal const string DefaultBaseUrl = "https://connect.mobiscroll.com/api/";

    private readonly HttpClient _http;
    private readonly bool _ownsHttpClient;
    private readonly MobiscrollConnectConfig _config;
    private readonly Uri _baseUri;

    private readonly SemaphoreSlim _refreshLock = new(1, 1);
    private Task<string>? _inflightRefresh;

    private TokenResponse? _credentials;
    private Action<TokenResponse>? _onTokensRefreshed;

    internal static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        PropertyNameCaseInsensitive = true,
        DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull,
        Converters = { new ProviderJsonConverter() },
    };

    public ApiClient(MobiscrollConnectConfig config, HttpClient? httpClient = null, string? baseUrl = null)
    {
        if (config is null)
        {
            throw new ArgumentNullException(nameof(config));
        }
        if (string.IsNullOrEmpty(config.ClientId) ||
            string.IsNullOrEmpty(config.ClientSecret) ||
            string.IsNullOrEmpty(config.RedirectUri))
        {
            throw new MobiscrollConnectException(
                "Client ID, Client Secret and Redirect URI are required");
        }

        _config = config;

        var resolved = baseUrl ?? DefaultBaseUrl;
        if (!resolved.EndsWith('/'))
        {
            resolved += "/";
        }
        _baseUri = new Uri(resolved, UriKind.Absolute);

        if (httpClient is null)
        {
            _http = new HttpClient { Timeout = TimeSpan.FromSeconds(30) };
            _ownsHttpClient = true;
        }
        else
        {
            _http = httpClient;
            _ownsHttpClient = false;
        }
    }

    public MobiscrollConnectConfig Config => _config;

    /// <summary>Base URL including trailing slash.</summary>
    public string BaseUrl => _baseUri.ToString();

    public TokenResponse? Credentials => _credentials;

    public void SetCredentials(TokenResponse tokens)
    {
        _credentials = tokens ?? throw new ArgumentNullException(nameof(tokens));
    }

    public void OnTokensRefreshed(Action<TokenResponse> callback)
    {
        _onTokensRefreshed = callback;
    }

    // ---- HTTP verbs ----------------------------------------------------------

    public Task<T?> GetAsync<T>(string path, CancellationToken ct = default)
        => SendAsync<T>(HttpMethod.Get, path, content: null, extraHeaders: null, allowRefresh: true, ct);

    public Task<T?> PostJsonAsync<T>(string path, object? body, CancellationToken ct = default)
        => SendAsync<T>(HttpMethod.Post, path, content: SerializeJson(body), extraHeaders: null, allowRefresh: true, ct);

    public Task<T?> PutJsonAsync<T>(string path, object? body, CancellationToken ct = default)
        => SendAsync<T>(HttpMethod.Put, path, content: SerializeJson(body), extraHeaders: null, allowRefresh: true, ct);

    public Task<T?> DeleteAsync<T>(string path, CancellationToken ct = default)
        => SendAsync<T>(HttpMethod.Delete, path, content: null, extraHeaders: null, allowRefresh: true, ct);

    public Task DeleteAsync(string path, CancellationToken ct = default)
        => SendAsync<object>(HttpMethod.Delete, path, content: null, extraHeaders: null, allowRefresh: true, ct);

    /// <summary>
    /// Form-encoded POST that bypasses the 401-refresh interceptor.
    /// Used exclusively for the OAuth token exchange / refresh endpoints.
    /// </summary>
    internal Task<T?> PostFormNoRefreshAsync<T>(
        string path,
        IEnumerable<KeyValuePair<string, string>> formValues,
        IDictionary<string, string> extraHeaders,
        CancellationToken ct = default)
    {
        var content = new FormUrlEncodedContent(formValues);
        return SendAsync<T>(HttpMethod.Post, path, content, extraHeaders, allowRefresh: false, ct);
    }

    // ---- Core send -----------------------------------------------------------

    private async Task<T?> SendAsync<T>(
        HttpMethod method,
        string path,
        HttpContent? content,
        IDictionary<string, string>? extraHeaders,
        bool allowRefresh,
        CancellationToken ct)
    {
        var request = BuildRequest(method, path, content, extraHeaders, attachBearer: allowRefresh);

        HttpResponseMessage response;
        try
        {
            response = await _http.SendAsync(request, HttpCompletionOption.ResponseContentRead, ct).ConfigureAwait(false);
        }
        catch (TaskCanceledException ex) when (!ct.IsCancellationRequested)
        {
            throw new NetworkException("Request timed out", ex);
        }
        catch (HttpRequestException ex)
        {
            throw new NetworkException(ex.Message, ex);
        }

        if (response.StatusCode == HttpStatusCode.Unauthorized
            && allowRefresh
            && !string.IsNullOrEmpty(_credentials?.RefreshToken))
        {
            response.Dispose();

            string newAccessToken;
            try
            {
                newAccessToken = await RefreshAccessTokenAsync(ct).ConfigureAwait(false);
            }
            catch
            {
                throw new AuthenticationException("Failed to refresh token");
            }

            // Retry original request exactly once.
            var retry = BuildRequest(method, path, CloneContent(content), extraHeaders, attachBearer: false);
            retry.Headers.Authorization = new AuthenticationHeaderValue("Bearer", newAccessToken);

            try
            {
                response = await _http.SendAsync(retry, HttpCompletionOption.ResponseContentRead, ct).ConfigureAwait(false);
            }
            catch (HttpRequestException ex)
            {
                throw new NetworkException(ex.Message, ex);
            }
        }

        using (response)
        {
            if (!response.IsSuccessStatusCode)
            {
                throw await BuildExceptionAsync(response, ct).ConfigureAwait(false);
            }

            if (typeof(T) == typeof(object))
            {
                return default;
            }

            var stream = await response.Content.ReadAsStreamAsync(ct).ConfigureAwait(false);
            if (stream.CanSeek && stream.Length == 0)
            {
                return default;
            }

            try
            {
                return await JsonSerializer.DeserializeAsync<T>(stream, JsonOptions, ct).ConfigureAwait(false);
            }
            catch (JsonException)
            {
                // Empty or non-JSON success body — return default (mirrors Node `response.data || ...`).
                return default;
            }
        }
    }

    private HttpRequestMessage BuildRequest(
        HttpMethod method,
        string path,
        HttpContent? content,
        IDictionary<string, string>? extraHeaders,
        bool attachBearer)
    {
        var relative = path.TrimStart('/');
        var uri = new Uri(_baseUri, relative);

        var request = new HttpRequestMessage(method, uri);
        if (content is not null)
        {
            request.Content = content;
        }

        if (attachBearer && !string.IsNullOrEmpty(_credentials?.AccessToken))
        {
            request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", _credentials!.AccessToken);
        }

        if (extraHeaders is not null)
        {
            foreach (var kv in extraHeaders)
            {
                // Authorization must go on request.Headers.Authorization; others pass-through.
                if (string.Equals(kv.Key, "Authorization", StringComparison.OrdinalIgnoreCase))
                {
                    var parts = kv.Value.Split(' ', 2);
                    request.Headers.Authorization = parts.Length == 2
                        ? new AuthenticationHeaderValue(parts[0], parts[1])
                        : new AuthenticationHeaderValue(kv.Value);
                }
                else
                {
                    request.Headers.TryAddWithoutValidation(kv.Key, kv.Value);
                }
            }
        }

        return request;
    }

    private static HttpContent? CloneContent(HttpContent? original)
    {
        if (original is null)
        {
            return null;
        }
        // For retry after 401 we only ever clone JSON or form bodies we built ourselves.
        // Reading the original to a byte[] is safe because we built it in-process.
        var bytes = original.ReadAsByteArrayAsync().GetAwaiter().GetResult();
        var copy = new ByteArrayContent(bytes);
        if (original.Headers.ContentType is not null)
        {
            copy.Headers.ContentType = original.Headers.ContentType;
        }
        return copy;
    }

    private static StringContent SerializeJson(object? body)
    {
        var json = body is null ? "{}" : JsonSerializer.Serialize(body, JsonOptions);
        return new StringContent(json, Encoding.UTF8, "application/json");
    }

    // ---- Token refresh -------------------------------------------------------

    private async Task<string> RefreshAccessTokenAsync(CancellationToken ct)
    {
        // Dedup: share one inflight refresh across concurrent 401s.
        Task<string> task;
        await _refreshLock.WaitAsync(ct).ConfigureAwait(false);
        try
        {
            if (_inflightRefresh is null || _inflightRefresh.IsCompleted)
            {
                _inflightRefresh = DoRefreshAsync(ct);
            }
            task = _inflightRefresh;
        }
        finally
        {
            _refreshLock.Release();
        }

        try
        {
            return await task.ConfigureAwait(false);
        }
        finally
        {
            await _refreshLock.WaitAsync(CancellationToken.None).ConfigureAwait(false);
            try
            {
                if (ReferenceEquals(_inflightRefresh, task))
                {
                    _inflightRefresh = null;
                }
            }
            finally
            {
                _refreshLock.Release();
            }
        }
    }

    private async Task<string> DoRefreshAsync(CancellationToken ct)
    {
        if (string.IsNullOrEmpty(_credentials?.RefreshToken))
        {
            throw new AuthenticationException("No refresh token available");
        }

        var basic = Convert.ToBase64String(
            Encoding.UTF8.GetBytes($"{_config.ClientId}:{_config.ClientSecret}"));

        var form = new List<KeyValuePair<string, string>>
        {
            new("grant_type", "refresh_token"),
            new("refresh_token", _credentials!.RefreshToken!),
            new("redirect_uri", _config.RedirectUri),
        };

        var headers = new Dictionary<string, string>
        {
            ["Authorization"] = $"Basic {basic}",
            ["CLIENT_ID"] = _config.ClientId,
        };

        TokenResponse? newTokens;
        try
        {
            newTokens = await PostFormNoRefreshAsync<TokenResponse>("/oauth/token", form, headers, ct)
                .ConfigureAwait(false);
        }
        catch
        {
            throw new AuthenticationException("Failed to refresh token");
        }

        if (newTokens is null || string.IsNullOrEmpty(newTokens.AccessToken))
        {
            throw new AuthenticationException("Failed to refresh token");
        }

        // Merge: keep existing refresh_token if server didn't issue a new one.
        _credentials = new TokenResponse
        {
            AccessToken = newTokens.AccessToken,
            TokenType = string.IsNullOrEmpty(newTokens.TokenType) ? _credentials.TokenType : newTokens.TokenType,
            ExpiresIn = newTokens.ExpiresIn ?? _credentials.ExpiresIn,
            RefreshToken = newTokens.RefreshToken ?? _credentials.RefreshToken,
        };

        _onTokensRefreshed?.Invoke(_credentials);

        return _credentials.AccessToken;
    }

    // ---- Error mapping -------------------------------------------------------

    private static async Task<MobiscrollConnectException> BuildExceptionAsync(
        HttpResponseMessage response,
        CancellationToken ct)
    {
        var status = (int)response.StatusCode;
        string? body = null;
        try
        {
            body = await response.Content.ReadAsStringAsync(ct).ConfigureAwait(false);
        }
        catch
        {
            // fall through with null body
        }

        string? message = null;
        string? codeField = null;
        JsonElement? details = null;

        if (!string.IsNullOrWhiteSpace(body))
        {
            try
            {
                using var doc = JsonDocument.Parse(body);
                var root = doc.RootElement;
                if (root.ValueKind == JsonValueKind.Object)
                {
                    if (root.TryGetProperty("message", out var m) && m.ValueKind == JsonValueKind.String)
                    {
                        message = m.GetString();
                    }
                    if (root.TryGetProperty("code", out var c) && c.ValueKind == JsonValueKind.String)
                    {
                        codeField = c.GetString();
                    }
                    if (root.TryGetProperty("details", out var d))
                    {
                        details = d.Clone();
                    }
                }
            }
            catch (JsonException)
            {
                // Non-JSON body — keep message null so we fall through to the status reason.
            }
        }

        message ??= response.ReasonPhrase ?? $"HTTP {status}";

        return status switch
        {
            401 or 403 => new AuthenticationException(message),
            404 => new NotFoundException(message),
            400 or 422 => new ValidationException(message, details),
            429 => new RateLimitException(message, ParseRetryAfter(response)),
            500 or 502 or 503 or 504 => new ServerException(message, status),
            _ => new MobiscrollConnectException(message, codeField ?? "MOBISCROLL_ERROR"),
        };
    }

    private static int? ParseRetryAfter(HttpResponseMessage response)
    {
        if (response.Headers.TryGetValues("Retry-After", out var values))
        {
            foreach (var v in values)
            {
                if (int.TryParse(v, System.Globalization.NumberStyles.Integer,
                    System.Globalization.CultureInfo.InvariantCulture, out var n))
                {
                    return n;
                }
            }
        }
        return null;
    }

    public void Dispose()
    {
        if (_ownsHttpClient)
        {
            _http.Dispose();
        }
        _refreshLock.Dispose();
    }
}
