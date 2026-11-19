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
    /**
     * {@code true} (default) expands recurring events into their occurrences within {@code start}
     * and {@code end}; {@code false} returns each series once, as its master event. Sent as
     * {@code singleEvents=true|false} only when set.
     */
    private final Boolean singleEvents;
    /**
     * With {@code singleEvents(true)}, also returns the deleted occurrences of series that still
     * exist, as events with {@code status} {@code "cancelled"}. Default {@code false}. Ignored with
     * {@code singleEvents(false)}. Applies to the normal list and to {@code eventIds}. Sent as
     * {@code includeCancelled=true|false} only when set.
     */
    private final Boolean includeCancelled;
    /**
     * Returns only these events, given as event ids per provider and calendar id, e.g.
     * {@code {GOOGLE: {"ana@example.com": ["abc123"]}}}. Sent as a single JSON-encoded
     * {@code eventIds} query parameter, like {@code calendarIds}:
     * {@code eventIds={"google":{"ana@example.com":["abc123"]}}}. It is sent whenever it is set,
     * including an empty map, which the server rejects with a 400 instead of listing every event.
     *
     * <p>At most 40 ids per request (more is rejected with a {@code ValidationException}). Cannot be
     * combined with {@code calendarIds} ({@code ValidationException}) and is not available with the
     * {@code free-busy} scope (HTTP 403, an {@code AuthenticationException}).
     *
     * <p>With {@code singleEvents(false)}, each event itself comes back (a series' master, or an
     * occurrence), in the order of the ids, without a time window. With {@code singleEvents(true)}
     * (default), each id returns what the list returns for it inside {@code start} and {@code end}:
     * the occurrences of a series, never its master, or a single event when it overlaps the window.
     * Missing or deleted ids are left out, without an error.
     *
     * <p>A {@code nextPageToken} from such a request is only valid with the same {@code eventIds},
     * {@code singleEvents}, {@code includeCancelled}, {@code start} and {@code end}.
     */
    private final Map<Provider, Map<String, List<String>>> eventIds;

    private EventListParams(Builder b) {
        this.calendarIds = b.calendarIds;
        this.start = b.start;
        this.end = b.end;
        this.pageSize = b.pageSize;
        this.nextPageToken = b.nextPageToken;
        this.singleEvents = b.singleEvents;
        this.includeCancelled = b.includeCancelled;
        this.eventIds = b.eventIds;
    }

    public Map<Provider, List<String>> getCalendarIds() { return calendarIds; }
    public OffsetDateTime getStart() { return start; }
    public OffsetDateTime getEnd() { return end; }
    public Integer getPageSize() { return pageSize; }
    public String getNextPageToken() { return nextPageToken; }
    public Boolean getSingleEvents() { return singleEvents; }
    public Boolean getIncludeCancelled() { return includeCancelled; }
    public Map<Provider, Map<String, List<String>>> getEventIds() { return eventIds; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Map<Provider, List<String>> calendarIds;
        private OffsetDateTime start;
        private OffsetDateTime end;
        private Integer pageSize;
        private String nextPageToken;
        private Boolean singleEvents;
        private Boolean includeCancelled;
        private Map<Provider, Map<String, List<String>>> eventIds;

        public Builder calendarIds(Map<Provider, List<String>> v) { this.calendarIds = v; return this; }
        public Builder start(OffsetDateTime v) { this.start = v; return this; }
        public Builder end(OffsetDateTime v) { this.end = v; return this; }
        public Builder pageSize(Integer v) { this.pageSize = v; return this; }
        public Builder nextPageToken(String v) { this.nextPageToken = v; return this; }
        public Builder singleEvents(Boolean v) { this.singleEvents = v; return this; }
        public Builder includeCancelled(Boolean v) { this.includeCancelled = v; return this; }
        public Builder eventIds(Map<Provider, Map<String, List<String>>> v) { this.eventIds = v; return this; }

        public EventListParams build() { return new EventListParams(this); }
    }
}
