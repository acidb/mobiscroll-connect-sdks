using System;
using System.Globalization;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using Mobiscroll.Connect.Internal;
using Mobiscroll.Connect.Models;

namespace Mobiscroll.Connect.Resources;

public sealed class Events
{
    private readonly ApiClient _client;

    internal Events(ApiClient client)
    {
        _client = client;
    }

    /// <summary>List events matching the given filters.</summary>
    public async Task<EventsListResponse> ListAsync(EventListParams? listParams = null, CancellationToken ct = default)
    {
        var path = "/events";
        if (listParams is not null)
        {
            var qs = new QueryStringBuilder()
                .Add("pageSize", listParams.PageSize)
                .Add("start", FormatDate(listParams.Start))
                .Add("end", FormatDate(listParams.End))
                .Add("nextPageToken", listParams.NextPageToken)
                .Add("singleEvents", listParams.SingleEvents);

            if (listParams.CalendarIds is { Count: > 0 })
            {
                qs.Add("calendarIds", JsonSerializer.Serialize(listParams.CalendarIds, ApiClient.JsonOptions));
            }

            if (!qs.IsEmpty)
            {
                path = $"/events?{qs}";
            }
        }

        var result = await _client.GetAsync<EventsListResponse>(path, ct).ConfigureAwait(false);
        return result ?? new EventsListResponse();
    }

    /// <summary>Create a new event on the given provider+calendar.</summary>
    public async Task<CalendarEvent> CreateAsync(EventCreateData data, CancellationToken ct = default)
    {
        if (data is null)
        {
            throw new ArgumentNullException(nameof(data));
        }
        var result = await _client.PostJsonAsync<CalendarEvent>("/event", data, ct).ConfigureAwait(false);
        return result ?? throw new InvalidOperationException("Event creation returned no body");
    }

    /// <summary>Update an existing event.</summary>
    public async Task<CalendarEvent> UpdateAsync(EventUpdateData data, CancellationToken ct = default)
    {
        if (data is null)
        {
            throw new ArgumentNullException(nameof(data));
        }
        var result = await _client.PutJsonAsync<CalendarEvent>("/event", data, ct).ConfigureAwait(false);
        return result ?? throw new InvalidOperationException("Event update returned no body");
    }

    /// <summary>Delete an event (optionally a single recurrence instance).</summary>
    public Task DeleteAsync(EventDeleteParams deleteParams, CancellationToken ct = default)
    {
        if (deleteParams is null)
        {
            throw new ArgumentNullException(nameof(deleteParams));
        }

        var qs = new QueryStringBuilder()
            .Add("provider", deleteParams.Provider.ToWireString())
            .Add("calendarId", deleteParams.CalendarId)
            .Add("eventId", deleteParams.EventId)
            .Add("recurringEventId", deleteParams.RecurringEventId)
            .Add("deleteMode", deleteParams.DeleteMode);

        var path = qs.IsEmpty ? "/event" : $"/event?{qs}";
        return _client.DeleteAsync(path, ct);
    }

    private static string? FormatDate(DateTime? value)
    {
        if (!value.HasValue) return null;
        var dt = value.Value;
        var utc = dt.Kind == DateTimeKind.Utc ? dt : dt.ToUniversalTime();
        return utc.ToString("yyyy-MM-ddTHH:mm:ss.fffZ", CultureInfo.InvariantCulture);
    }
}
