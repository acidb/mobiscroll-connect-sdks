package com.mobiscroll.connect.models;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.mobiscroll.connect.Provider;

/** Parameters for {@code Events.list}. All fields are optional. */
public final class EventListParams {

    /**
     * Provider-keyed map of calendar IDs to fetch from. Serialized as a single
     * JSON-encoded {@code calendarIds} query parameter, e.g.
     * {@code calendarIds={"google":["primary"],"microsoft":["AAMk..."]}}.
     */
    private final Map<Provider, List<String>> calendarIds;
    private final OffsetDateTime start;
    private final OffsetDateTime end;
    private final Integer pageSize;
    private final String nextPageToken;
    private final Boolean singleEvents;

    private EventListParams(Builder b) {
        this.calendarIds = b.calendarIds;
        this.start = b.start;
        this.end = b.end;
        this.pageSize = b.pageSize;
        this.nextPageToken = b.nextPageToken;
        this.singleEvents = b.singleEvents;
    }

    public Map<Provider, List<String>> getCalendarIds() { return calendarIds; }
    public OffsetDateTime getStart() { return start; }
    public OffsetDateTime getEnd() { return end; }
    public Integer getPageSize() { return pageSize; }
    public String getNextPageToken() { return nextPageToken; }
    public Boolean getSingleEvents() { return singleEvents; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Map<Provider, List<String>> calendarIds;
        private OffsetDateTime start;
        private OffsetDateTime end;
        private Integer pageSize;
        private String nextPageToken;
        private Boolean singleEvents;

        public Builder calendarIds(Map<Provider, List<String>> v) { this.calendarIds = v; return this; }
        public Builder start(OffsetDateTime v) { this.start = v; return this; }
        public Builder end(OffsetDateTime v) { this.end = v; return this; }
        public Builder pageSize(Integer v) { this.pageSize = v; return this; }
        public Builder nextPageToken(String v) { this.nextPageToken = v; return this; }
        public Builder singleEvents(Boolean v) { this.singleEvents = v; return this; }

        public EventListParams build() { return new EventListParams(this); }
    }
}
