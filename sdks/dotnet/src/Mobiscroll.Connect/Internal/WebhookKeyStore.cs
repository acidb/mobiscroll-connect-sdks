using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;

namespace Mobiscroll.Connect.Internal;

/// <summary>
/// Process-wide cache of the webhook public keys published at one keys URL, shared by every
/// client instance. Follows the endpoint's <c>Cache-Control</c>, re-fetches at most once a
/// minute, and keeps the last good keys when a fetch fails.
/// </summary>
internal sealed class WebhookKeyStore
{
    private static readonly TimeSpan DefaultMaxAge = TimeSpan.FromHours(1);
    private static readonly TimeSpan MaxMaxAge = TimeSpan.FromHours(24);
    private static readonly TimeSpan MinRefetchInterval = TimeSpan.FromSeconds(60);
    private static readonly TimeSpan FetchTimeout = TimeSpan.FromSeconds(10);

    private static readonly ConcurrentDictionary<string, WebhookKeyStore> Stores = new(StringComparer.Ordinal);

    private readonly string _url;
    private readonly SemaphoreSlim _gate = new(1, 1);
    private volatile Snapshot _snapshot = Snapshot.Empty;
    private long _lastAttemptTicks = long.MinValue;
    private Task? _inFlight;

    private WebhookKeyStore(string url)
    {
        _url = url;
    }

    /// <summary>Clock for cache ages and the delivery timestamp check; replaced by tests.</summary>
    internal static TimeProvider Clock { get; set; } = TimeProvider.System;

    public static WebhookKeyStore ForUrl(string url) => Stores.GetOrAdd(url, static u => new WebhookKeyStore(u));

    /// <summary>Clears every cached key set; for tests.</summary>
    internal static void Reset() => Stores.Clear();

    /// <summary>
    /// Returns the cached keys, fetching them first when they are missing or stale and the
    /// once-a-minute limit allows. Joins a fetch already in flight.
    /// </summary>
    public async Task<IReadOnlyList<string>> GetKeysAsync(HttpClient http, CancellationToken ct)
    {
        Task? pending;
        await _gate.WaitAsync(ct).ConfigureAwait(false);
        try
        {
            var now = Clock.GetUtcNow();
            var snapshot = _snapshot;
            if (_inFlight is { IsCompleted: false })
            {
                pending = _inFlight;
            }
            else if (now - snapshot.FetchedAt > snapshot.MaxAge && CanRefetch(now))
            {
                pending = StartFetch(http, now);
            }
            else
            {
                pending = null;
            }
        }
        finally
        {
            _gate.Release();
        }

        if (pending is not null)
        {
            await pending.WaitAsync(ct).ConfigureAwait(false);
        }
        return _snapshot.Keys;
    }

    /// <summary>
    /// Re-fetches the keys unless a fetch was attempted within the last minute; joins a fetch
    /// already in flight. Returns <c>false</c> when no fetch was made or joined.
    /// </summary>
    public async Task<bool> TryRefreshAsync(HttpClient http, CancellationToken ct)
    {
        Task pending;
        await _gate.WaitAsync(ct).ConfigureAwait(false);
        try
        {
            var now = Clock.GetUtcNow();
            if (_inFlight is { IsCompleted: false })
            {
                pending = _inFlight;
            }
            else if (CanRefetch(now))
            {
                pending = StartFetch(http, now);
            }
            else
            {
                return false;
            }
        }
        finally
        {
            _gate.Release();
        }

        await pending.WaitAsync(ct).ConfigureAwait(false);
        return true;
    }

    private bool CanRefetch(DateTimeOffset now)
    {
        var last = _lastAttemptTicks;
        return last == long.MinValue || now.UtcTicks - last >= MinRefetchInterval.Ticks;
    }

    // Called with _gate held. The fetch is deliberately not tied to any caller's token: other
    // callers may be waiting on it.
    private Task StartFetch(HttpClient http, DateTimeOffset now)
    {
        _lastAttemptTicks = now.UtcTicks;
        _inFlight = FetchAsync(http);
        return _inFlight;
    }

    private async Task FetchAsync(HttpClient http)
    {
        try
        {
            using var timeout = new CancellationTokenSource(FetchTimeout);
            using var request = new HttpRequestMessage(HttpMethod.Get, _url);
            request.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));
            using var response = await http
                .SendAsync(request, HttpCompletionOption.ResponseContentRead, timeout.Token)
                .ConfigureAwait(false);
            if (!response.IsSuccessStatusCode)
            {
                return;
            }

            var body = await response.Content.ReadAsByteArrayAsync(timeout.Token).ConfigureAwait(false);
            var keys = ParseKeys(body);
            if (keys.Count > 0)
            {
                _snapshot = new Snapshot(keys, Clock.GetUtcNow(), ParseMaxAge(response.Headers.CacheControl));
            }
        }
        catch (Exception)
        {
            // Keep the last good keys; the caller falls back to a pinned key when there are none.
        }
    }

    private static List<string> ParseKeys(byte[] body)
    {
        var keys = new List<string>();
        using var document = JsonDocument.Parse(body);
        if (document.RootElement.ValueKind != JsonValueKind.Object
            || !document.RootElement.TryGetProperty("keys", out var entries)
            || entries.ValueKind != JsonValueKind.Array)
        {
            return keys;
        }

        foreach (var entry in entries.EnumerateArray())
        {
            if (entry.ValueKind != JsonValueKind.Object
                || !entry.TryGetProperty("key", out var key)
                || key.ValueKind != JsonValueKind.String)
            {
                continue;
            }
            var value = key.GetString()!;
            if (!value.StartsWith(WebhookVerifier.PublicKeyPrefix, StringComparison.Ordinal))
            {
                continue;
            }
            if (IsEd25519(entry))
            {
                keys.Add(value);
            }
        }
        return keys;
    }

    private static bool IsEd25519(JsonElement entry)
    {
        if (!entry.TryGetProperty("alg", out var alg) || alg.ValueKind == JsonValueKind.Null)
        {
            return true;
        }
        if (alg.ValueKind != JsonValueKind.String)
        {
            return false;
        }
        var name = alg.GetString();
        return string.IsNullOrEmpty(name) || string.Equals(name, "ed25519", StringComparison.OrdinalIgnoreCase);
    }

    private static TimeSpan ParseMaxAge(CacheControlHeaderValue? cacheControl)
    {
        if (cacheControl?.MaxAge is not { } maxAge)
        {
            return DefaultMaxAge;
        }
        return maxAge > MaxMaxAge ? MaxMaxAge : maxAge;
    }

    private sealed record Snapshot(IReadOnlyList<string> Keys, DateTimeOffset FetchedAt, TimeSpan MaxAge)
    {
        public static readonly Snapshot Empty = new(Array.Empty<string>(), DateTimeOffset.MinValue, DefaultMaxAge);
    }
}
