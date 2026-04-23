using System;

namespace Mobiscroll.Connect.Exceptions;

public class MobiscrollConnectException : Exception
{
    public string CodeString { get; }

    public MobiscrollConnectException(string message, string codeString = "MOBISCROLL_ERROR")
        : base(message)
    {
        CodeString = codeString;
    }

    public MobiscrollConnectException(string message, string codeString, Exception innerException)
        : base(message, innerException)
    {
        CodeString = codeString;
    }
}
