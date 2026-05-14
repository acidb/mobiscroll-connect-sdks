package com.mobiscroll.connect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.mobiscroll.connect.models.Calendar;
import com.mobiscroll.connect.support.ClientFactory;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CalendarsTest {

    private MockWebServer server;

    @BeforeEach void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach void tearDown() throws Exception {
        server.shutdown();
    }

    @Test void listParsesCalendars() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "[{\"id\":\"cal1\",\"provider\":\"google\",\"title\":\"Work\",\"timeZone\":\"UTC\",\"color\":\"#4285F4\"}]"));
        MobiscrollConnectClient client = ClientFactory.withMockAndCredentials(server);

        List<Calendar> calendars = client.calendars().list();

        assertThat(calendars).hasSize(1);
        Calendar c = calendars.get(0);
        assertThat(c.getId()).isEqualTo("cal1");
        assertThat(c.getProvider()).isEqualTo(Provider.GOOGLE);
        assertThat(c.getTitle()).isEqualTo("Work");
        assertThat(c.getTimeZone()).isEqualTo("UTC");
        assertThat(c.getColor()).isEqualTo("#4285F4");

        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("GET");
        assertThat(req.getPath()).isEqualTo("/api/calendars");
    }
}
