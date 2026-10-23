package com.mobiscroll.connect.models;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Conference options sent with an event create or update ({@code conferenceData}). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class EventConferenceData {

    private final Boolean autoGenerate;
    private final String provider;
    private final Map<String, Object> additional;

    private EventConferenceData(Builder b) {
        this.autoGenerate = b.autoGenerate;
        this.provider = b.provider;
        this.additional = new LinkedHashMap<>(b.additional);
    }

    /**
     * {@code true} generates a provider meeting link where supported (Google Meet, Microsoft Teams); the
     * event's {@code conference} URL and the other fields here are then ignored.
     */
    @JsonProperty("autoGenerate") public Boolean getAutoGenerate() { return autoGenerate; }

    /** Optional; the calendar provider decides the conference type when the event is written. */
    @JsonProperty("provider") public String getProvider() { return provider; }

    /** Further provider-specific conference fields, sent as top-level keys. */
    @JsonAnyGetter public Map<String, Object> getAdditional() { return additional; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Boolean autoGenerate;
        private String provider;
        private final Map<String, Object> additional = new LinkedHashMap<>();

        public Builder autoGenerate(Boolean v) { this.autoGenerate = v; return this; }
        public Builder provider(String v) { this.provider = v; return this; }
        public Builder put(String key, Object value) { this.additional.put(key, value); return this; }

        public EventConferenceData build() { return new EventConferenceData(this); }
    }
}
