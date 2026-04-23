namespace Mobiscroll.Connect.Exceptions;

public sealed class NotFoundException : MobiscrollConnectException
{
    public NotFoundException(string message) : base(message, "NOT_FOUND_ERROR") { }
}
