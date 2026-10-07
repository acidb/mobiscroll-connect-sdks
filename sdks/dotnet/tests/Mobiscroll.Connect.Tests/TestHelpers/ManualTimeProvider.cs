using System;

namespace Mobiscroll.Connect.Tests.TestHelpers;

/// <summary>A clock the test moves by hand.</summary>
internal sealed class ManualTimeProvider : TimeProvider
{
    public ManualTimeProvider(DateTimeOffset now)
    {
        Now = now;
    }

    public DateTimeOffset Now { get; set; }

    public override DateTimeOffset GetUtcNow() => Now;

    public void Advance(TimeSpan by) => Now += by;
}
