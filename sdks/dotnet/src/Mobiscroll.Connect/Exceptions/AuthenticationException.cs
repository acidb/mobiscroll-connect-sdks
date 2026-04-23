namespace Mobiscroll.Connect.Exceptions;

public sealed class AuthenticationException : MobiscrollConnectException
{
    public AuthenticationException(string message) : base(message, "AUTHENTICATION_ERROR") { }
}
