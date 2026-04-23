using System;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Options;

namespace Mobiscroll.Connect.DependencyInjection;

public static class ServiceCollectionExtensions
{
    private const string HttpClientName = "Mobiscroll.Connect";

    /// <summary>
    /// Register <see cref="MobiscrollConnectClient"/> and its config with the DI container.
    /// Uses <see cref="IHttpClientFactory"/> for the underlying HTTP client.
    /// </summary>
    public static IServiceCollection AddMobiscrollConnect(
        this IServiceCollection services,
        Action<MobiscrollConnectConfig> configure)
    {
        if (services is null)
        {
            throw new ArgumentNullException(nameof(services));
        }
        if (configure is null)
        {
            throw new ArgumentNullException(nameof(configure));
        }

        services.AddOptions<MobiscrollConnectConfig>().Configure(configure);
        services.AddHttpClient(HttpClientName, c => c.Timeout = TimeSpan.FromSeconds(30));

        services.AddSingleton(sp =>
        {
            var options = sp.GetRequiredService<IOptions<MobiscrollConnectConfig>>().Value;
            var factory = sp.GetRequiredService<IHttpClientFactory>();
            var http = factory.CreateClient(HttpClientName);
            return new MobiscrollConnectClient(options, http);
        });

        return services;
    }
}
