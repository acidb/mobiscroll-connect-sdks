using System.Net.Http;
using Mobiscroll.Connect;

namespace Mobiscroll.Connect.Tests.TestHelpers;

internal static class ClientFactory
{
    public static MobiscrollConnectClient Create(FakeHttpMessageHandler handler, string baseUrl = "https://connect.mobiscroll.com/api/")
    {
        var http = new HttpClient(handler);
        var config = new MobiscrollConnectConfig
        {
            ClientId = "test-client-id",
            ClientSecret = "test-client-secret",
            RedirectUri = "http://localhost:5000/callback",
        };
        return new MobiscrollConnectClient(config, http, baseUrl);
    }
}
