namespace Mobiscroll.Connect.Models;

public sealed class DisconnectParams
{
    public Provider Provider { get; set; }

    /// <summary>
    /// <c>Id</c> of the account to disconnect, from <c>GetConnectionStatusAsync</c>
    /// (<c>Connections[provider][].Id</c>). Null disconnects every account of the provider.
    /// </summary>
    public string? Account { get; set; }
}
