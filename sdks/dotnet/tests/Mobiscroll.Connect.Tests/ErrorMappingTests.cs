using System.Net;
using System.Threading.Tasks;
using Mobiscroll.Connect.Exceptions;
using Mobiscroll.Connect.Models;
using Mobiscroll.Connect.Tests.TestHelpers;

namespace Mobiscroll.Connect.Tests;

public class ErrorMappingTests
{
    [Theory]
    [InlineData(HttpStatusCode.Forbidden, typeof(AuthenticationException))]
    [InlineData(HttpStatusCode.NotFound, typeof(NotFoundException))]
    [InlineData(HttpStatusCode.BadRequest, typeof(ValidationException))]
    [InlineData((HttpStatusCode)422, typeof(ValidationException))]
    [InlineData(HttpStatusCode.InternalServerError, typeof(ServerException))]
    [InlineData(HttpStatusCode.BadGateway, typeof(ServerException))]
    [InlineData(HttpStatusCode.ServiceUnavailable, typeof(ServerException))]
    [InlineData(HttpStatusCode.GatewayTimeout, typeof(ServerException))]
    public async Task StatusCodeMapsToExpectedException(HttpStatusCode status, System.Type expected)
    {
        var handler = new FakeHttpMessageHandler()
            .Enqueue(status, """{"message":"boom"}""");

        using var client = ClientFactory.Create(handler);
        client.SetCredentials(new TokenResponse { AccessToken = "at" });

        var ex = await Assert.ThrowsAsync(expected, () => client.Calendars.ListAsync());
        Assert.Contains("boom", ex.Message);
    }

    [Fact]
    public async Task RateLimitException_CapturesRetryAfterHeader()
    {
        var handler = new FakeHttpMessageHandler()
            .Enqueue(HttpStatusCode.TooManyRequests, """{"message":"slow down"}""",
                new System.Collections.Generic.Dictionary<string, string> { ["Retry-After"] = "42" });

        using var client = ClientFactory.Create(handler);
        client.SetCredentials(new TokenResponse { AccessToken = "at" });

        var ex = await Assert.ThrowsAsync<RateLimitException>(() => client.Calendars.ListAsync());
        Assert.Equal(42, ex.RetryAfter);
    }

    [Fact]
    public async Task ValidationException_CapturesDetailsField()
    {
        var handler = new FakeHttpMessageHandler()
            .Enqueue(HttpStatusCode.BadRequest, """{"message":"bad","details":{"field":"title"}}""");

        using var client = ClientFactory.Create(handler);
        client.SetCredentials(new TokenResponse { AccessToken = "at" });

        var ex = await Assert.ThrowsAsync<ValidationException>(() => client.Calendars.ListAsync());
        Assert.NotNull(ex.Details);
        Assert.Equal("title", ex.Details!.Value.GetProperty("field").GetString());
    }
}
