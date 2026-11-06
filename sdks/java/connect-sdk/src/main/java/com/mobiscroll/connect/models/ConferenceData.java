package com.mobiscroll.connect.models;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** The conference of a {@link CalendarEvent}, with the same fields on every provider. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ConferenceData {

    private final String provider;
    private final String url;
    private final Boolean autoGenerate;
    private final Map<String, Object> original;

    @JsonCreator
    public ConferenceData(
            @JsonProperty("provider") String provider,
            @JsonProperty("url") String url,
            @JsonProperty("autoGenerate") Boolean autoGenerate,
            @JsonProperty("original") Map<String, Object> original) {
        this.provider = provider;
        this.url = url;
        this.autoGenerate = autoGenerate;
        this.original = original;
    }

    /** The conference system: {@code hangoutsMeet}, {@code google-meet}, {@code teams} or {@code caldav}. */
    @JsonProperty("provider") public String getProvider() { return provider; }

    /** The join URL, the same value as {@link CalendarEvent#getConference()}. */
    @JsonProperty("url") public String getUrl() { return url; }

    /** Whether a meeting link was requested on create or update. */
    @JsonProperty("autoGenerate") public Boolean getAutoGenerate() { return autoGenerate; }

    /**
     * The conference data exactly as the calendar provider returns it: Google's {@code conferenceData},
     * Microsoft's {@code onlineMeeting}; {@code null} for Apple and CalDAV.
     */
    @JsonProperty("original") public Map<String, Object> getOriginal() { return original; }
}
