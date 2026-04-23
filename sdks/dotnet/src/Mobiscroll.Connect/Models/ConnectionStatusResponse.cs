using System.Collections.Generic;

namespace Mobiscroll.Connect.Models;

public sealed class ConnectedAccount
{
    public string Id { get; set; } = string.Empty;
    public string? Display { get; set; }
}

public sealed class ProviderConnections
{
    public List<ConnectedAccount> Google { get; set; } = new();
    public List<ConnectedAccount> Microsoft { get; set; } = new();
    public List<ConnectedAccount> Apple { get; set; } = new();
    public List<ConnectedAccount> CalDav { get; set; } = new();
}

public sealed class ConnectionStatusResponse
{
    public ProviderConnections Connections { get; set; } = new();
    public bool LimitReached { get; set; }
    public int? Limit { get; set; }
}
