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
/// </summary>
internal sealed class FakeHttpMessageHandler : HttpMessageHandler
{
    private readonly Queue<Func<HttpRequestMessage, HttpResponseMessage>> _responders = new();
    public List<RecordedRequest> Requests { get; } = new();

    public FakeHttpMessageHandler Enqueue(HttpStatusCode status, string? jsonBody = null, IDictionary<string, string>? headers = null)
    {
        _responders.Enqueue(_ =>
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
        return this;
    }

    public FakeHttpMessageHandler EnqueueRaw(Func<HttpRequestMessage, HttpResponseMessage> factory)
    {
        _responders.Enqueue(factory);
        return this;
    }

    protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
    {
        string? body = null;
        if (request.Content is not null)
        {
            body = await request.Content.ReadAsStringAsync(cancellationToken).ConfigureAwait(false);
        }

        Requests.Add(new RecordedRequest(
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
        return _responders.Dequeue()(request);
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
