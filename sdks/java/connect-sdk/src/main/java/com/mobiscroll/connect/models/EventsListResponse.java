package com.mobiscroll.connect.models;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Paginated response from {@code Events.list}. */
public final class EventsListResponse {

    private final List<CalendarEvent> events;
    private final Integer pageSize;
    private final String nextPageToken;

    @JsonCreator
    public EventsListResponse(
            @JsonProperty("events") List<CalendarEvent> events,
            @JsonProperty("pageSize") Integer pageSize,
            @JsonProperty("nextPageToken") String nextPageToken) {
        this.events = events;
        this.pageSize = pageSize;
        this.nextPageToken = nextPageToken;
    }

    public List<CalendarEvent> getEvents() { return events; }
    public Integer getPageSize() { return pageSize; }
    public String getNextPageToken() { return nextPageToken; }
}
