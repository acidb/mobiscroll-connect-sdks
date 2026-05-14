package com.mobiscroll.connect;

import static org.assertj.core.api.Assertions.assertThat;

import com.mobiscroll.connect.models.TokenResponse;
import com.mobiscroll.connect.support.ClientFactory;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

class ApiClientTest {

    private MockWebServer server;

    @BeforeEach void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach void tearDown() throws Exception {
        server.shutdown();
    }

    @Test void refreshesTokenOn401AndRetries() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"error\":\"expired\"}"));
        server.enqueue(new MockResponse().setBody(
                "{\"access_token\":\"access-2\",\"token_type\":\"Bearer\",\"expires_in\":3600,\"refresh_token\":\"refresh-2\"}"));
        server.enqueue(new MockResponse().setBody("[]"));

        AtomicReference<TokenResponse> seen = new AtomicReference<>();
        MobiscrollConnectClient client = new MobiscrollConnectClient(MobiscrollConnectConfig.builder()
                .clientId("test-client").clientSecret("test-secret").redirectUri("http://localhost/cb")
                .baseUrl(server.url("/api").toString())
                .onTokensRefreshed(seen::set)
                .build());
        client.setCredentials(new TokenResponse("access-1", "Bearer", 3600, "refresh-1", null));

        client.calendars().list();

        assertThat(server.getRequestCount()).isEqualTo(3);
        RecordedRequest first = server.takeRequest();
        assertThat(first.getPath()).isEqualTo("/api/calendars");
        assertThat(first.getHeader("Authorization")).isEqualTo("Bearer access-1");

        RecordedRequest refresh = server.takeRequest();
        assertThat(refresh.getPath()).isEqualTo("/api/oauth/token");
        assertThat(refresh.getHeader("Authorization")).startsWith("Basic ");
        assertThat(refresh.getHeader("CLIENT_ID")).isEqualTo("test-client");
        assertThat(refresh.getBody().readUtf8()).contains("grant_type=refresh_token").contains("refresh_token=refresh-1");

        RecordedRequest retry = server.takeRequest();
        assertThat(retry.getPath()).isEqualTo("/api/calendars");
        assertThat(retry.getHeader("Authorization")).isEqualTo("Bearer access-2");

        assertThat(seen.get()).isNotNull();
        assertThat(seen.get().getAccessToken()).isEqualTo("access-2");
        assertThat(seen.get().getRefreshToken()).isEqualTo("refresh-2");
    }

    @Test void preservesOldRefreshTokenWhenServerOmitsIt() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(401));
        server.enqueue(new MockResponse().setBody(
                "{\"access_token\":\"access-2\",\"token_type\":\"Bearer\",\"expires_in\":3600}"));
        server.enqueue(new MockResponse().setBody("[]"));

        MobiscrollConnectClient client = ClientFactory.withMockAndCredentials(server);
        client.calendars().list();

        assertThat(client.getCredentials().getRefreshToken()).isEqualTo("refresh-1");
        assertThat(client.getCredentials().getAccessToken()).isEqualTo("access-2");
    }

    @Test void bearerHeaderIsInjectedOnAuthenticatedCalls() throws Exception {
        server.enqueue(new MockResponse().setBody("[]"));
        MobiscrollConnectClient client = ClientFactory.withMockAndCredentials(server);
        client.calendars().list();
        RecordedRequest r = server.takeRequest();
        assertThat(r.getHeader("Authorization")).isEqualTo("Bearer access-1");
        assertThat(r.getHeader("Accept")).isEqualTo("application/json");
    }
}
