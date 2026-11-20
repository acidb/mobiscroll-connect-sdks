using System;
using System.Collections.Generic;

namespace Mobiscroll.Connect.Models;

public sealed class EventListParams
{
    public int? PageSize { get; set; }
    public DateTime? Start { get; set; }
    public DateTime? End { get; set; }

    /// <summary>
    /// Calendar IDs grouped by provider wire string, e.g. { "google": ["primary"], "microsoft": ["..."] }.
    /// Serialized as JSON inside a single <c>calendarIds</c> query parameter to match Node/PHP wire format.
    /// </summary>
    public Dictionary<string, List<string>>? CalendarIds { get; set; }

    /// <summary>
    /// A <see cref="EventsListResponse.NextPageToken"/> from the previous page. When the request used
    /// <see cref="EventIds"/>, it is only valid with the same <see cref="EventIds"/>, <see cref="SingleEvents"/>,
    /// <see cref="IncludeCancelled"/>, <see cref="Start"/> and <see cref="End"/>.
    /// </summary>
    public string? NextPageToken { get; set; }

    /// <summary>
    /// <c>true</c> (default) expands recurring events into their occurrences within <see cref="Start"/> and
    /// <see cref="End"/>; <c>false</c> returns each series once, as its master event. Sent only when set.
    /// </summary>
    public bool? SingleEvents { get; set; }

    /// <summary>
    /// With <c>SingleEvents = true</c>, also returns the deleted occurrences of series that still exist, as
    /// events with <c>Status = "cancelled"</c>. Default <c>false</c>. Ignored with <c>SingleEvents = false</c>.
    /// Applies to the normal list and to <see cref="EventIds"/>. Sent only when set; an explicit
    /// <c>false</c> is sent.
    /// </summary>
    public bool? IncludeCancelled { get; set; }

    /// <summary>
    /// Returns only these events, given as event IDs per provider wire string and calendar ID, e.g.
    /// <c>{ "google": { "ana@example.com": ["abc123"] } }</c>. Serialized as JSON inside a single
    /// <c>eventIds</c> query parameter, like <see cref="CalendarIds"/>, and sent whenever it is set: an empty
    /// dictionary is sent too, and the server rejects it with a <see cref="Exceptions.ValidationException"/>.
    /// At most 40 IDs per request (more is rejected with a <see cref="Exceptions.ValidationException"/>).
    /// Cannot be combined with <see cref="CalendarIds"/> (<see cref="Exceptions.ValidationException"/>) and is
    /// not available with the <c>free-busy</c> scope (HTTP 403, an <see cref="Exceptions.AuthenticationException"/>).
    /// <para>
    /// With <c>SingleEvents = false</c>, each series master or single event comes back (never an
    /// occurrence, modified or not), in the order of the IDs, without a time window. With <c>SingleEvents = true</c> (default), each ID returns
    /// what the list returns for it inside <see cref="Start"/> and <see cref="End"/>: the occurrences of a
    /// series, never its master, or a single event when it overlaps the window. Missing or deleted IDs are
    /// left out, without an error.
    /// </para>
    /// </summary>
    public Dictionary<string, Dictionary<string, List<string>>>? EventIds { get; set; }
}
