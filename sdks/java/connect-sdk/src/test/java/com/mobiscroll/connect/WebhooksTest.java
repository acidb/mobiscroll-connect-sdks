package com.mobiscroll.connect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mobiscroll.connect.exceptions.AuthenticationException;
import com.mobiscroll.connect.exceptions.ValidationException;
import com.mobiscroll.connect.models.WebhookSubscribeParams;
import com.mobiscroll.connect.models.WebhookSubscribeResponse;
import com.mobiscroll.connect.models.WebhookUnsubscribeParams;
import com.mobiscroll.connect.models.WebhookUnsubscribeResponse;
import com.mobiscroll.connect.support.ClientFactory;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebhooksTest {

    private MockWebServer server;

    @BeforeEach void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach void tearDown() throws Exception {
        server.shutdown();
    }

    @Test void subscribeWebhookSendsJsonBodyAndParsesResponse() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"success\":true,\"provider\":\"google\",\"subscription\":{\"channelId\":\"chan-1\","
                        + "\"resourceId\":\"res-1\",\"expiration\":\"2026-07-01T00:00:00Z\"},"
                        + "\"serverWebhookUrl\":\"https://connect.example/hooks/google\",\"channelId\":\"chan-1\"}"));
        MobiscrollConnectClient client = ClientFactory.withMockAndCredentials(server);

        WebhookSubscribeResponse resp = client.webhooks().subscribeWebhook(WebhookSubscribeParams.builder()
                .provider(Provider.GOOGLE)
                .calendarId("cal-1")
                .channelId("chan-1")
                .expiration(1780000000000L)
                .build());

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getProvider()).isEqualTo("google");
        assertThat(resp.getServerWebhookUrl()).isEqualTo("https://connect.example/hooks/google");
        assertThat(resp.getChannelId()).isEqualTo("chan-1");
        assertThat(resp.getSubscription().getChannelId()).isEqualTo("chan-1");
        assertThat(resp.getSubscription().getResourceId()).isEqualTo("res-1");
        assertThat(resp.getSubscription().getExpiration()).isEqualTo("2026-07-01T00:00:00Z");

        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getPath()).isEqualTo("/api/subscribe-webhook");
        assertThat(req.getHeader("Content-Type")).startsWith("application/json");
        assertThat(req.getHeader("Authorization")).isEqualTo("Bearer access-1");
        String body = req.getBody().readUtf8();
        assertThat(body).contains("\"provider\":\"google\"");
        assertThat(body).contains("\"calendarId\":\"cal-1\"");
        assertThat(body).contains("\"channelId\":\"chan-1\"");
        assertThat(body).contains("\"expiration\":1780000000000");
    }

    @Test void subscribeWebhookOmitsOptionalFieldsWhenNotSet() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"success\":true,\"provider\":\"microsoft\",\"subscription\":{\"channelId\":\"auto-chan\"},"
                        + "\"serverWebhookUrl\":\"https://connect.example/hooks/microsoft\",\"channelId\":\"auto-chan\"}"));
        MobiscrollConnectClient client = ClientFactory.withMockAndCredentials(server);

        WebhookSubscribeResponse resp = client.webhooks().subscribeWebhook(WebhookSubscribeParams.builder()
                .provider(Provider.MICROSOFT)
                .calendarId("cal-2")
                .build());

        assertThat(resp.getSubscription().getChannelId()).isEqualTo("auto-chan");
        assertThat(resp.getSubscription().getResourceId()).isNull();

        RecordedRequest req = server.takeRequest();
        String body = req.getBody().readUtf8();
        assertThat(body).doesNotContain("channelId");
        assertThat(body).doesNotContain("expiration");
    }

    @Test void unsubscribeWebhookSendsJsonBodyAndParsesResponse() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"success\":true}"));
        MobiscrollConnectClient client = ClientFactory.withMockAndCredentials(server);

        WebhookUnsubscribeResponse resp = client.webhooks().unsubscribeWebhook(WebhookUnsubscribeParams.builder()
                .provider(Provider.GOOGLE)
                .channelId("chan-1")
                .resourceId("res-1")
                .build());

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getMessage()).isNull();

        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getPath()).isEqualTo("/api/unsubscribe-webhook");
        String body = req.getBody().readUtf8();
        assertThat(body).contains("\"provider\":\"google\"");
        assertThat(body).contains("\"channelId\":\"chan-1\"");
        assertThat(body).contains("\"resourceId\":\"res-1\"");
    }

    @Test void unsubscribeWebhookSurfacesSuccessTrueWithMessageOnProviderSideFailure() throws Exception {
        // Backend still returns success:true (with an explanatory message) after removing the
        // local mapping, even when the provider-side unsubscribe itself failed (e.g. expired).
        server.enqueue(new MockResponse().setBody(
                "{\"success\":true,\"message\":\"Subscription already expired upstream\"}"));
        MobiscrollConnectClient client = ClientFactory.withMockAndCredentials(server);

        WebhookUnsubscribeResponse resp = client.webhooks().unsubscribeWebhook(WebhookUnsubscribeParams.builder()
                .provider(Provider.GOOGLE)
                .channelId("chan-1")
                .build());

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getMessage()).isEqualTo("Subscription already expired upstream");
    }

    @Test void subscribeWebhookMapsFourHundredToValidation() {
        server.enqueue(new MockResponse().setResponseCode(400)
                .setBody("{\"message\":\"provider is required\"}"));
        MobiscrollConnectClient client = ClientFactory.withMockAndCredentials(server);

        assertThatThrownBy(() -> client.webhooks().subscribeWebhook(WebhookSubscribeParams.builder()
                .provider(Provider.GOOGLE)
                .calendarId("cal-1")
                .build()))
                .isInstanceOf(ValidationException.class)
                .satisfies(t -> assertThat(t.getMessage()).isEqualTo("provider is required"));
    }

    @Test void subscribeWebhookMapsFourOhOneToAuthentication() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"message\":\"unauthorized\"}"));
        MobiscrollConnectClient client = ClientFactory.withMock(server);

        assertThatThrownBy(() -> client.webhooks().subscribeWebhook(WebhookSubscribeParams.builder()
                .provider(Provider.GOOGLE)
                .calendarId("cal-1")
                .build()))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test void unsubscribeWebhookMapsFourHundredToValidation() {
        server.enqueue(new MockResponse().setResponseCode(400)
                .setBody("{\"message\":\"channelId is required\"}"));
        MobiscrollConnectClient client = ClientFactory.withMockAndCredentials(server);

        assertThatThrownBy(() -> client.webhooks().unsubscribeWebhook(WebhookUnsubscribeParams.builder()
                .provider(Provider.GOOGLE)
                .channelId("chan-1")
                .build()))
                .isInstanceOf(ValidationException.class)
                .satisfies(t -> assertThat(t.getMessage()).isEqualTo("channelId is required"));
    }

    @Test void unsubscribeWebhookMapsFiveHundredToServer() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("{\"message\":\"boom\"}"));
        MobiscrollConnectClient client = ClientFactory.withMockAndCredentials(server);

        assertThatThrownBy(() -> client.webhooks().unsubscribeWebhook(WebhookUnsubscribeParams.builder()
                .provider(Provider.GOOGLE)
                .channelId("chan-1")
                .build()))
                .isInstanceOf(com.mobiscroll.connect.exceptions.ServerException.class);
    }
}
