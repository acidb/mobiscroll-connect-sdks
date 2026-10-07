using System;
using System.Collections.Generic;
using System.Net;
using System.Net.Http;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace Mobiscroll.Connect.Tests.TestHelpers;

/// <summary>
/// Minimal HttpMessageHandler that returns pre-scripted responses in FIFO order.
/// Records every request it sees so tests can assert on method, uri, headers, body.
/// Safe to call from concurrent requests.
/// </summary>
internal sealed class FakeHttpMessageHandler : HttpMessageHandler
{
    private readonly object _sync = new();
    private readonly Queue<Func<HttpRequestMessage, Task<HttpResponseMessage>>> _responders = new();
    private readonly List<RecordedRequest> _requests = new();

    public List<RecordedRequest> Requests
    {
        get
        {
            lock (_sync)
            {
                return new List<RecordedRequest>(_requests);
            }
        }
    }

    public FakeHttpMessageHandler Enqueue(HttpStatusCode status, string? jsonBody = null, IDictionary<string, string>? headers = null)
    {
        return EnqueueRaw(_ =>
        {
            var resp = new HttpResponseMessage(status);
            if (jsonBody is not null)
            {
                resp.Content = new StringContent(jsonBody, Encoding.UTF8, "application/json");
            }
            if (headers is not null)
            {
                foreach (var kv in headers)
                {
                    resp.Headers.TryAddWithoutValidation(kv.Key, kv.Value);
                }
            }
            return resp;
        });
    }

    public FakeHttpMessageHandler EnqueueRaw(Func<HttpRequestMessage, HttpResponseMessage> factory)
    {
        return EnqueueAsync(request => Task.FromResult(factory(request)));
    }

    /// <summary>Queue a responder that completes later, e.g. to hold a request in flight.</summary>
    public FakeHttpMessageHandler EnqueueAsync(Func<HttpRequestMessage, Task<HttpResponseMessage>> factory)
    {
        lock (_sync)
        {
            _responders.Enqueue(factory);
        }
        return this;
    }

    protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
    {
        string? body = null;
        if (request.Content is not null)
        {
            body = await request.Content.ReadAsStringAsync(cancellationToken).ConfigureAwait(false);
        }

        Func<HttpRequestMessage, Task<HttpResponseMessage>> responder;
        lock (_sync)
        {
            _requests.Add(new RecordedRequest(
                request.Method,
                request.RequestUri!,
                new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
                {
                    ["Authorization"] = request.Headers.Authorization?.ToString() ?? string.Empty,
                    ["CLIENT_ID"] = HeaderOrEmpty(request, "CLIENT_ID"),
                    ["Content-Type"] = request.Content?.Headers.ContentType?.ToString() ?? string.Empty,
                },
                body));

            if (_responders.Count == 0)
            {
                throw new InvalidOperationException($"No response queued for {request.Method} {request.RequestUri}");
            }
            responder = _responders.Dequeue();
        }
        return await responder(request).ConfigureAwait(false);
    }

    private static string HeaderOrEmpty(HttpRequestMessage request, string name)
    {
        if (request.Headers.TryGetValues(name, out var values))
        {
            foreach (var v in values) return v;
        }
        return string.Empty;
    }
}

internal sealed record RecordedRequest(
    HttpMethod Method,
    Uri Uri,
    IReadOnlyDictionary<string, string> Headers,
    string? Body);
