package com.mobiscroll.connect.models;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Conference details on a {@link WebhookEvent}. Deliveries carry an object here, while the events
 * endpoints return the join URL as a string in {@link CalendarEvent#getConference()}.
 */
public final class WebhookConference {

    private final String url;
    private final String provider;
    private final String meetingProvider;
    private final Map<String, Object> data;

    @JsonCreator
    public WebhookConference(
            @JsonProperty("url") String url,
            @JsonProperty("provider") String provider,
            @JsonProperty("meetingProvider") String meetingProvider,
            @JsonProperty("data") Map<String, Object> data) {
        this.url = url;
        this.provider = provider;
        this.meetingProvider = meetingProvider;
        this.data = data;
    }

    /** Conference join URL. */
    public String getUrl() { return url; }
    /** Conference provider as the calendar provider reports it, e.g. {@code hangoutsMeet}, {@code teams}. */
    public String getProvider() { return provider; }
    /** Same value as {@link #getProvider()}. */
    public String getMeetingProvider() { return meetingProvider; }
    /** Provider-specific conference payload; {@code null} when absent. */
    public Map<String, Object> getData() { return data; }
}
