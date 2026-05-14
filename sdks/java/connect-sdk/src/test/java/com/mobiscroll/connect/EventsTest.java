package com.mobiscroll.connect;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import com.mobiscroll.connect.models.CalendarEvent;
import com.mobiscroll.connect.models.EventCreateData;
import com.mobiscroll.connect.models.EventDeleteParams;
import com.mobiscroll.connect.models.EventListParams;
import com.mobiscroll.connect.models.EventUpdateData;
import com.mobiscroll.connect.models.EventsListResponse;
import com.mobiscroll.connect.support.ClientFactory;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventsTest {

    private MockWebServer server;

    @BeforeEach void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach void tearDown() throws Exception {
        server.shutdown();
    }

    @Test void listPassesQueryParamsAndParsesResponse() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"events\":[{\"id\":\"e1\",\"provider\":\"google\",\"calendarId\":\"c1\",\"title\":\"Meet\"}],\"pageSize\":50,\"nextPageToken\":\"tok\"}"));
        MobiscrollConnectClient client = ClientFactory.withMockAndCredentials(server);

        Map<Provider, java.util.List<String>> calIds = new LinkedHashMap<>();
        calIds.put(Provider.GOOGLE, Arrays.asList("c1", "c2"));
        EventsListResponse resp = client.events().list(EventListParams.builder()
                .calendarIds(calIds)
                .start(OffsetDateTime.of(2026, 5, 1, 0, 0, 0, 0, ZoneOffset.UTC))
                .end(OffsetDateTime.of(2026, 5, 31, 23, 59, 59, 0, ZoneOffset.UTC))
                .singleEvents(true)
                .pageSize(50)
                .build());

        assertThat(resp.getEvents()).hasSize(1);
        assertThat(resp.getPageSize()).isEqualTo(50);
        assertThat(resp.getNextPageToken()).isEqualTo("tok");

        RecordedRequest req = server.takeRequest();
        assertThat(req.getPath()).startsWith("/api/events?");
        // calendarIds is JSON-encoded as a single map keyed by provider
        assertThat(req.getRequestUrl().queryParameter("calendarIds"))
                .isEqualTo("{\"google\":[\"c1\",\"c2\"]}");
        assertThat(req.getPath()).contains("singleEvents=true");
        assertThat(req.getPath()).contains("pageSize=50");
    }

    @Test void createSendsJsonBody() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"id\":\"e1\",\"provider\":\"google\",\"calendarId\":\"c1\",\"title\":\"Meet\",\"start\":\"2026-05-01T09:00:00Z\",\"end\":\"2026-05-01T10:00:00Z\"}"));
        MobiscrollConnectClient client = ClientFactory.withMockAndCredentials(server);

        CalendarEvent ev = client.events().create(EventCreateData.builder()
                .provider(Provider.GOOGLE)
                .calendarId("c1")
                .title("Meet")
                .start(OffsetDateTime.of(2026, 5, 1, 9, 0, 0, 0, ZoneOffset.UTC))
                .end(OffsetDateTime.of(2026, 5, 1, 10, 0, 0, 0, ZoneOffset.UTC))
                .build());

        assertThat(ev.getId()).isEqualTo("e1");
        assertThat(ev.getStart()).isEqualTo(OffsetDateTime.of(2026, 5, 1, 9, 0, 0, 0, ZoneOffset.UTC));

        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getPath()).isEqualTo("/api/event");
        assertThat(req.getHeader("Content-Type")).startsWith("application/json");
        String body = req.getBody().readUtf8();
        assertThat(body).contains("\"provider\":\"google\"");
        assertThat(body).contains("\"title\":\"Meet\"");
        assertThat(body).contains("\"start\":\"2026-05-01T09:00:00Z\"");
    }

    @Test void updatePutsToEventPath() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"id\":\"e1\",\"provider\":\"google\",\"calendarId\":\"c1\",\"title\":\"Renamed\"}"));
        MobiscrollConnectClient client = ClientFactory.withMockAndCredentials(server);

        CalendarEvent ev = client.events().update(EventUpdateData.builder()
                .provider(Provider.GOOGLE).calendarId("c1").eventId("e1").title("Renamed").build());

        assertThat(ev.getTitle()).isEqualTo("Renamed");
        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("PUT");
        assertThat(req.getPath()).isEqualTo("/api/event");
    }

    @Test void deleteSendsQueryParams() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));
        MobiscrollConnectClient client = ClientFactory.withMockAndCredentials(server);

        client.events().delete(EventDeleteParams.builder()
                .provider(Provider.GOOGLE).calendarId("c1").eventId("e1")
                .deleteMode("all").build());

        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("DELETE");
        assertThat(req.getPath()).startsWith("/api/event?");
        assertThat(req.getPath()).contains("provider=google");
        assertThat(req.getPath()).contains("calendarId=c1");
        assertThat(req.getPath()).contains("eventId=e1");
        assertThat(req.getPath()).contains("deleteMode=all");
    }
}
