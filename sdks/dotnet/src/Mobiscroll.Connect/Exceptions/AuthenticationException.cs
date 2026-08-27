namespace Mobiscroll.Connect.Exceptions;

public class AuthenticationException : MobiscrollConnectException
{
    public AuthenticationException(string message) : base(message, "AUTHENTICATION_ERROR") { }

    protected AuthenticationException(string message, string code) : base(message, code) { }
}
