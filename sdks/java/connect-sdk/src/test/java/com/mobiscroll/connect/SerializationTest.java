package com.mobiscroll.connect;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mobiscroll.connect.models.Calendar;
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
}
