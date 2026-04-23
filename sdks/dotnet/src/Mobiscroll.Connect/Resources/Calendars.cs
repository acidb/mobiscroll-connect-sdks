using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;
using Mobiscroll.Connect.Models;

namespace Mobiscroll.Connect.Resources;

public sealed class Calendars
{
    private readonly ApiClient _client;

    internal Calendars(ApiClient client)
    {
        _client = client;
    }

    /// <summary>List all calendars from every connected provider.</summary>
    public async Task<IReadOnlyList<Calendar>> ListAsync(CancellationToken ct = default)
    {
        var result = await _client.GetAsync<List<Calendar>>("/calendars", ct).ConfigureAwait(false);
        return result ?? new List<Calendar>();
    }
}
