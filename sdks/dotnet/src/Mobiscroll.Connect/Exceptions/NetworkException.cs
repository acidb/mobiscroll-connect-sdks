using System;

namespace Mobiscroll.Connect.Exceptions;

public sealed class NetworkException : MobiscrollConnectException
{
    public NetworkException(string message) : base(message, "NETWORK_ERROR") { }

    public NetworkException(string message, Exception innerException)
        : base(message, "NETWORK_ERROR", innerException) { }
}
