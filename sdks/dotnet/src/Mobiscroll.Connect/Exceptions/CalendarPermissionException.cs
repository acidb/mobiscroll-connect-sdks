using System.Collections.Generic;
using Mobiscroll.Connect.Models;

namespace Mobiscroll.Connect.Exceptions;

/// <summary>
/// Thrown when no connected account has the calendar access the request needs.
/// </summary>
/// <remarks>
/// The user completed sign-in but did not grant the calendar permission — Google's consent
/// screen presents it as a separate checkbox. This cannot be repaired server-side, because
/// providers only issue permissions at consent time: the accounts in <see cref="Accounts"/>
/// have to run the connect flow again and allow access.
///
/// Derives from <see cref="AuthenticationException"/>, so existing handlers keep working.
/// </remarks>
public sealed class CalendarPermissionException : AuthenticationException
{
    public CalendarPermissionException(string message, IReadOnlyList<BlockedAccount>? accounts = null)
        : base(message, "CALENDAR_PERMISSION_REQUIRED")
    {
        Accounts = accounts ?? new List<BlockedAccount>();
    }

    /// <summary>Connected accounts that withheld calendar access and must reconnect.</summary>
    public IReadOnlyList<BlockedAccount> Accounts { get; }
}
