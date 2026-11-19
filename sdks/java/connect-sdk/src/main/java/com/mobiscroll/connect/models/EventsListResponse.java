package com.mobiscroll.connect.models;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Paginated response from {@code Events.list}. */
public final class EventsListResponse {

    private final List<CalendarEvent> events;
    private final Integer pageSize;
    private final String nextPageToken;
    private final Map<String, String> info;

    @JsonCreator
    public EventsListResponse(
            @JsonProperty("events") List<CalendarEvent> events,
            @JsonProperty("pageSize") Integer pageSize,
            @JsonProperty("nextPageToken") String nextPageToken,
            @JsonProperty("info") Map<String, String> info) {
        this.events = events;
        this.pageSize = pageSize;
        this.nextPageToken = nextPageToken;
        this.info = info;
    }

    public EventsListResponse(List<CalendarEvent> events, Integer pageSize, String nextPageToken) {
        this(events, pageSize, nextPageToken, null);
    }

    public List<CalendarEvent> getEvents() { return events; }
    public Integer getPageSize() { return pageSize; }
    public String getNextPageToken() { return nextPageToken; }
    /**
     * Messages keyed by provider name (the lowercase wire value, e.g. {@code "google"}), present when
     * a provider could not be read, and, with {@code eventIds}, when no connected account can read a
     * calendar (that calendar's ids are then left out of {@link #getEvents()}). {@code null} when the
     * response has none.
     */
    public Map<String, String> getInfo() { return info; }
}
