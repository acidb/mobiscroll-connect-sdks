package com.mobiscroll.connect;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mobiscroll.connect.models.Calendar;
import com.mobiscroll.connect.models.CalendarEvent;
import com.mobiscroll.connect.models.ConnectionStatus;
import com.mobiscroll.connect.models.TokenResponse;
import org.junit.jupiter.api.Test;

class SerializationTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test void providerDeserialisesFromLowercaseString() throws Exception {
        Provider p = mapper.readValue("\"google\"", Provider.class);
        assertThat(p).isEqualTo(Provider.GOOGLE);
    }

    @Test void providerSerialisesToLowercaseString() throws Exception {
        String json = mapper.writeValueAsString(Provider.MICROSOFT);
        assertThat(json).isEqualTo("\"microsoft\"");
    }

    @Test void tokenResponseDeserialisesSnakeCase() throws Exception {
        TokenResponse t = mapper.readValue(
                "{\"access_token\":\"a\",\"token_type\":\"Bearer\",\"expires_in\":60,\"refresh_token\":\"r\"}",
                TokenResponse.class);
        assertThat(t.getAccessToken()).isEqualTo("a");
        assertThat(t.getTokenType()).isEqualTo("Bearer");
        assertThat(t.getExpiresIn()).isEqualTo(60);
        assertThat(t.getRefreshToken()).isEqualTo("r");
    }

    @Test void tokenMergePreservesRefreshTokenWhenMissing() {
        TokenResponse oldT = new TokenResponse("a1", "Bearer", 60, "r1", null);
        TokenResponse incoming = new TokenResponse("a2", "Bearer", 60, null, null);
        TokenResponse merged = oldT.mergedWith(incoming);
        assertThat(merged.getAccessToken()).isEqualTo("a2");
        assertThat(merged.getRefreshToken()).isEqualTo("r1");
    }

    @Test void calendarDeserialisationCapturesOriginalAndUnknown() throws Exception {
        Calendar c = mapper.readValue(
                "{\"id\":\"cal1\",\"provider\":\"caldav\",\"title\":\"Personal\"," +
                "\"original\":{\"raw\":\"value\"},\"extraField\":\"keep\"}",
                Calendar.class);
        assertThat(c.getProvider()).isEqualTo(Provider.CALDAV);
        assertThat(c.getTitle()).isEqualTo("Personal");
        assertThat(c.getOriginal().get("raw").asText()).isEqualTo("value");
        assertThat(c.getAdditional()).containsEntry("extraField", "keep");
    }

    @Test void calendarEventDeserialisesDescriptionConferenceDataAndLastModified() throws Exception {
        CalendarEvent e = mapper.readValue(
                "{\"id\":\"evt-1\",\"provider\":\"google\",\"calendarId\":\"primary\",\"title\":\"Standup\"," +
                "\"description\":\"Weekly team sync\"," +
                "\"conference\":\"https://meet.google.com/abc-defg-hij\"," +
                "\"conferenceData\":{\"provider\":\"google-meet\",\"conferenceId\":\"abc-defg-hij\"}," +
                "\"lastModified\":\"2026-03-10T13:36:08.000Z\"}",
                CalendarEvent.class);
        assertThat(e.getDescription()).isEqualTo("Weekly team sync");
        assertThat(e.getConferenceData()).containsEntry("provider", "google-meet");
        assertThat(e.getLastModified()).isEqualTo("2026-03-10T13:36:08.000Z");
    }

    @Test void connectionStatusReportsCalendarPermissionPerAccount() throws Exception {
        ConnectionStatus s = mapper.readValue(
                "{\"connections\":{\"google\":[" +
                "{\"id\":\"granted@g.com\",\"grantedScopes\":[\"openid\",\"https://www.googleapis.com/auth/calendar\"]," +
                "\"calendarPermissionGranted\":true}," +
                "{\"id\":\"withheld@g.com\",\"grantedScopes\":[\"openid\"],\"calendarPermissionGranted\":false}]," +
                "\"apple\":[{\"id\":\"u@icloud.com\",\"grantedScopes\":[],\"calendarPermissionGranted\":null}]}," +
                "\"limitReached\":false}",
                ConnectionStatus.class);
        assertThat(s.getConnections().get(Provider.GOOGLE).get(0).getCalendarPermissionGranted()).isTrue();
        assertThat(s.getConnections().get(Provider.GOOGLE).get(0).getGrantedScopes())
                .contains("https://www.googleapis.com/auth/calendar");
        assertThat(s.getConnections().get(Provider.GOOGLE).get(1).getCalendarPermissionGranted()).isFalse();
        // Apple has no scopes to withhold, so the flag is null rather than false.
        assertThat(s.getConnections().get(Provider.APPLE).get(0).getCalendarPermissionGranted()).isNull();
    }

    @Test void connectedAccountDefaultsWhenScopeFieldsAbsent() throws Exception {
        ConnectionStatus s = mapper.readValue(
                "{\"connections\":{\"google\":[{\"id\":\"u@g.com\"}]},\"limitReached\":false}",
                ConnectionStatus.class);
        assertThat(s.getConnections().get(Provider.GOOGLE).get(0).getGrantedScopes()).isEmpty();
        assertThat(s.getConnections().get(Provider.GOOGLE).get(0).getCalendarPermissionGranted()).isNull();
    }
}
