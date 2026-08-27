namespace Mobiscroll.Connect.Models;

/// <summary>A connected account that withheld calendar access on its provider's consent screen.</summary>
public sealed class BlockedAccount
{
    public string Provider { get; set; } = string.Empty;
    public string Account { get; set; } = string.Empty;
}
