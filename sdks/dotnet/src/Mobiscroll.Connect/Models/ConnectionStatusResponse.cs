using System.Collections.Generic;

namespace Mobiscroll.Connect.Models;

public sealed class ConnectedAccount
{
    public string Id { get; set; } = string.Empty;
    public string? Display { get; set; }

    /// <summary>
    /// Scopes the provider actually granted for this account — not necessarily the ones
    /// Connect asked for. Google's consent screen lets the user untick the calendar
    /// permission and still complete sign-in. Empty for Apple and CalDav, which
    /// authenticate with a username and app password.
    /// </summary>
    public List<string> GrantedScopes { get; set; } = new();

    /// <summary>
    /// Whether this account granted calendar access sufficient for your project's scope.
    /// <c>false</c> means the account is connected but no calendars can be read from it
    /// until the user reconnects and allows access. <c>null</c> means the question does
    /// not apply (Apple, CalDav) or no scopes were recorded for the account.
    /// </summary>
    public bool? CalendarPermissionGranted { get; set; }
}

public sealed class ConnectionStatusResponse
{
    /// <summary>
    /// Connected accounts keyed by lowercase provider name (<c>"google"</c>, <c>"microsoft"</c>,
    /// <c>"apple"</c>, <c>"caldav"</c>). Matches the wire shape returned by the API.
    /// </summary>
    public Dictionary<string, List<ConnectedAccount>> Connections { get; set; } = new();
    public bool LimitReached { get; set; }
}
