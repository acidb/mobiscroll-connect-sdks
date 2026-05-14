package com.mobiscroll.connect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mobiscroll.connect.exceptions.AuthenticationException;
import com.mobiscroll.connect.exceptions.NotFoundException;
import com.mobiscroll.connect.exceptions.RateLimitException;
import com.mobiscroll.connect.exceptions.ServerException;
import com.mobiscroll.connect.exceptions.ValidationException;
import com.mobiscroll.connect.support.ClientFactory;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ErrorMappingTest {

    private MockWebServer server;
    private MobiscrollConnectClient client;

    @BeforeEach void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        client = ClientFactory.withMockAndCredentials(server);
    }

    @AfterEach void tearDown() throws Exception { server.shutdown(); }

    @Test void mapsFourHundredToValidation() {
        server.enqueue(new MockResponse().setResponseCode(400)
                .setBody("{\"message\":\"bad\",\"details\":{\"field\":\"title\"}}"));
        assertThatThrownBy(() -> client.calendars().list())
                .isInstanceOf(ValidationException.class)
                .satisfies(t -> {
                    ValidationException ve = (ValidationException) t;
                    assertThat(ve.getMessage()).isEqualTo("bad");
                    assertThat(ve.getDetails().get("field").asText()).isEqualTo("title");
                });
    }

    @Test void mapsFourOhFourToNotFound() {
        server.enqueue(new MockResponse().setResponseCode(404).setBody("{\"message\":\"missing\"}"));
        assertThatThrownBy(() -> client.calendars().list()).isInstanceOf(NotFoundException.class);
    }

    @Test void mapsFourOhThreeToAuthentication() {
        server.enqueue(new MockResponse().setResponseCode(403).setBody("{\"message\":\"no scope\"}"));
        assertThatThrownBy(() -> client.calendars().list()).isInstanceOf(AuthenticationException.class);
    }

    @Test void mapsFourTwentyNineToRateLimitWithRetryAfter() {
        server.enqueue(new MockResponse().setResponseCode(429)
                .addHeader("Retry-After", "42").setBody("{\"message\":\"slow down\"}"));
        assertThatThrownBy(() -> client.calendars().list())
                .isInstanceOf(RateLimitException.class)
                .satisfies(t -> assertThat(((RateLimitException) t).getRetryAfter()).isEqualTo(42));
    }

    @Test void mapsFiveHundredToServer() {
        server.enqueue(new MockResponse().setResponseCode(503).setBody("{\"message\":\"down\"}"));
        assertThatThrownBy(() -> client.calendars().list())
                .isInstanceOf(ServerException.class)
                .satisfies(t -> assertThat(((ServerException) t).getStatusCode()).isEqualTo(503));
    }

    @Test void surfacesAuthenticationAfterRefreshFails() {
        // initial 401 → refresh also 401 → expect AuthenticationException
        server.enqueue(new MockResponse().setResponseCode(401));
        server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"message\":\"bad refresh\"}"));
        assertThatThrownBy(() -> client.calendars().list()).isInstanceOf(AuthenticationException.class);
    }
}
