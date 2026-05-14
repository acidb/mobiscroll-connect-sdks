package com.mobiscroll.connect.support;

import com.mobiscroll.connect.MobiscrollConnectClient;
import com.mobiscroll.connect.MobiscrollConnectConfig;
import com.mobiscroll.connect.models.TokenResponse;
import okhttp3.mockwebserver.MockWebServer;

/** Helpers to build a {@link MobiscrollConnectClient} pointed at a {@link MockWebServer}. */
public final class ClientFactory {

    private ClientFactory() {}

    public static MobiscrollConnectClient withMock(MockWebServer server) {
        return new MobiscrollConnectClient(MobiscrollConnectConfig.builder()
                .clientId("test-client")
                .clientSecret("test-secret")
                .redirectUri("http://localhost/callback")
                .baseUrl(server.url("/api").toString())
                .build());
    }

    public static MobiscrollConnectClient withMockAndCredentials(MockWebServer server) {
        MobiscrollConnectClient c = withMock(server);
        c.setCredentials(new TokenResponse("access-1", "Bearer", 3600, "refresh-1", "calendars events"));
        return c;
    }
}
