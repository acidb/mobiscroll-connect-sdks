package com.mobiscroll.connect;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mobiscroll.connect.models.CalendarEvent;
import com.mobiscroll.connect.models.EventConferenceData;
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

    private static final String EMPTY_LIST = "{\"events\":[]}";

    @Test void listSendsEventIdsJsonEncodedInTheQuery() throws Exception {
        server.enqueue(new MockResponse().setBody(EMPTY_LIST));
        MobiscrollConnectClient client = ClientFactory.withMockAndCredentials(server);

        Map<Provider, Map<String, List<String>>> ids = new LinkedHashMap<>();
        Map<String, List<String>> google = new LinkedHashMap<>();
        google.put("ana@example.com", Arrays.asList("abc123", "def456"));
        ids.put(Provider.GOOGLE, google);
        ids.put(Provider.MICROSOFT, Map.of("AAMk", List.of("x1")));
        client.events().list(EventListParams.builder().eventIds(ids).build());

        RecordedRequest req = server.takeRequest();
        assertThat(req.getRequestUrl().queryParameter("eventIds")).isEqualTo(
                "{\"google\":{\"ana@example.com\":[\"abc123\",\"def456\"]},\"microsoft\":{\"AAMk\":[\"x1\"]}}");
        assertThat(req.getRequestUrl().querySize()).isEqualTo(1);
    }

    @Test void listSendsEventIdsWithSingleEventsFalse() throws Exception {
        server.enqueue(new MockResponse().setBody(EMPTY_LIST));
        MobiscrollConnectClient client = ClientFactory.withMockAndCredentials(server);

        client.events().list(EventListParams.builder()
                .eventIds(Map.of(Provider.GOOGLE, Map.of("ana@example.com", List.of("abc123"))))
                .singleEvents(false)
                .build());

        RecordedRequest req = server.takeRequest();
        assertThat(req.getRequestUrl().queryParameter("singleEvents")).isEqualTo("false");
        assertThat(req.getRequestUrl().queryParameter("eventIds"))
                .isEqualTo("{\"google\":{\"ana@example.com\":[\"abc123\"]}}");
        assertThat(req.getRequestUrl().queryParameterNames()).doesNotContain("calendarIds");
    }

    @Test void listSendsAnEmptyEventIdsMapAsEmptyObject() throws Exception {
        server.enqueue(new MockResponse().setBody(EMPTY_LIST));
        MobiscrollConnectClient client = ClientFactory.withMockAndCredentials(server);

        client.events().list(EventListParams.builder().eventIds(new LinkedHashMap<>()).build());

        // never dropped: dropping it would turn the filter into a list of every event
        RecordedRequest req = server.takeRequest();
        assertThat(req.getPath()).isEqualTo("/api/events?eventIds=%7B%7D");
        assertThat(req.getRequestUrl().queryParameter("eventIds")).isEqualTo("{}");
    }

    @Test void listSendsIncludeCancelledTrueAndFalse() throws Exception {
        server.enqueue(new MockResponse().setBody(EMPTY_LIST));
        server.enqueue(new MockResponse().setBody(EMPTY_LIST));
        MobiscrollConnectClient client = ClientFactory.withMockAndCredentials(server);

        client.events().list(EventListParams.builder().includeCancelled(true).build());
        client.events().list(EventListParams.builder().includeCancelled(false).build());

        assertThat(server.takeRequest().getPath()).isEqualTo("/api/events?includeCancelled=true");
        assertThat(server.takeRequest().getPath()).isEqualTo("/api/events?includeCancelled=false");
    }

    @Test void listDoesNotSendIncludeCancelledOrEventIdsWhenUnset() throws Exception {
        server.enqueue(new MockResponse().setBody(EMPTY_LIST));
        MobiscrollConnectClient client = ClientFactory.withMockAndCredentials(server);

        client.events().list(EventListParams.builder().singleEvents(true).build());

        assertThat(server.takeRequest().getPath()).isEqualTo("/api/events?singleEvents=true");
    }

    @Test void listReturnsInfoWhenPresentAndNullWhenAbsent() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"events\":[],\"info\":{\"google\":\"No connected account can read calendar ana@example.com\"}}"));
        server.enqueue(new MockResponse().setBody(EMPTY_LIST));
        MobiscrollConnectClient client = ClientFactory.withMockAndCredentials(server);

        EventsListResponse withInfo = client.events().list(EventListParams.builder().build());
        EventsListResponse withoutInfo = client.events().list(EventListParams.builder().build());

        assertThat(withInfo.getInfo())
                .containsExactly(Map.entry("google", "No connected account can read calendar ana@example.com"));
        assertThat(withoutInfo.getInfo()).isNull();
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
                .conference("https://meet.example.com/x")
                .conferenceData(EventConferenceData.builder().autoGenerate(true).build())
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
        assertThat(body).contains("\"conference\":\"https://meet.example.com/x\"");
        assertThat(body).contains("\"conferenceData\":{\"autoGenerate\":true}");
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
