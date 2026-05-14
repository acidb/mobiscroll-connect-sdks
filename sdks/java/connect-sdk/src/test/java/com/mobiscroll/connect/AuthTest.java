package com.mobiscroll.connect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import com.mobiscroll.connect.models.AuthUrlParams;
import com.mobiscroll.connect.models.DisconnectParams;
import com.mobiscroll.connect.models.DisconnectResponse;
import com.mobiscroll.connect.models.TokenResponse;
import com.mobiscroll.connect.support.ClientFactory;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthTest {

    private MockWebServer server;

    @BeforeEach void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach void tearDown() throws Exception {
        server.shutdown();
    }

    @Test void generateAuthUrlIncludesAllParams() {
        MobiscrollConnectClient client = ClientFactory.withMock(server);
        String url = client.auth().generateAuthUrl(AuthUrlParams.builder()
                .userId("user-1")
                .state("xyz")
                .scope("calendars events")
                .providers(Arrays.asList(Provider.GOOGLE, Provider.MICROSOFT))
                .build());

        assertThat(url).startsWith(server.url("/api/oauth/authorize").toString() + "?");
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("client_id=test-client");
        assertThat(url).contains("redirect_uri=http");
        assertThat(url).contains("user_id=user-1");
        assertThat(url).contains("state=xyz");
        assertThat(url).contains("scope=calendars+events");
        assertThat(url).contains("providers=google");
        assertThat(url).contains("providers=microsoft");
    }

    @Test void getTokenExchangesCodeAndCachesCredentials() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"access_token\":\"a\",\"token_type\":\"Bearer\",\"expires_in\":3600,\"refresh_token\":\"r\"}"));
        MobiscrollConnectClient client = ClientFactory.withMock(server);

        TokenResponse t = client.auth().getToken("the-code");

        assertThat(t.getAccessToken()).isEqualTo("a");
        assertThat(client.getCredentials()).isSameAs(t);

        RecordedRequest req = server.takeRequest();
        assertThat(req.getPath()).isEqualTo("/api/oauth/token");
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getHeader("Authorization")).startsWith("Basic ");
        assertThat(req.getHeader("CLIENT_ID")).isEqualTo("test-client");
        String body = req.getBody().readUtf8();
        assertThat(body).contains("grant_type=authorization_code");
        assertThat(body).contains("code=the-code");
    }

    @Test void disconnectFallsBackToLegacyPathOn404() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(404).setBody("{\"message\":\"not found\"}"));
        server.enqueue(new MockResponse().setBody("{\"success\":true}"));

        MobiscrollConnectClient client = ClientFactory.withMockAndCredentials(server);
        DisconnectResponse r = client.auth().disconnect(DisconnectParams.builder()
                .provider(Provider.GOOGLE).account("alice@example.com").build());

        assertThat(r.isSuccess()).isTrue();
        RecordedRequest first = server.takeRequest();
        assertThat(first.getMethod()).isEqualTo("POST");
        assertThat(first.getPath()).startsWith("/api/oauth/disconnect?");
        RecordedRequest second = server.takeRequest();
        assertThat(second.getMethod()).isEqualTo("POST");
        assertThat(second.getPath()).startsWith("/api/disconnect?");
        assertThat(second.getPath()).contains("provider=google");
        assertThat(second.getPath()).contains("account=alice%40example.com");
    }
}
