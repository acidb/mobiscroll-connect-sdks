namespace Mobiscroll.Connect.Exceptions;

public sealed class ServerException : MobiscrollConnectException
{
    public int StatusCode { get; }

    public ServerException(string message, int statusCode)
        : base(message, "SERVER_ERROR")
    {
        StatusCode = statusCode;
    }
}
