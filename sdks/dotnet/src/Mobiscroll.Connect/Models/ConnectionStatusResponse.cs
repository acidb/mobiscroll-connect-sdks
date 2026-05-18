using System.Collections.Generic;

namespace Mobiscroll.Connect.Models;

public sealed class ConnectedAccount
{
    public string Id { get; set; } = string.Empty;
    public string? Display { get; set; }
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
