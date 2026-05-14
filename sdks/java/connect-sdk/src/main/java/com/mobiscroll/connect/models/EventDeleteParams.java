package com.mobiscroll.connect.models;

import java.util.Objects;

import com.mobiscroll.connect.Provider;

/** Parameters for {@code Events.delete}. */
public final class EventDeleteParams {

    private final Provider provider;
    private final String calendarId;
    private final String eventId;
    private final String recurringEventId;
    private final String deleteMode;

    private EventDeleteParams(Builder b) {
        this.provider = Objects.requireNonNull(b.provider, "provider");
        this.calendarId = Objects.requireNonNull(b.calendarId, "calendarId");
        this.eventId = Objects.requireNonNull(b.eventId, "eventId");
        this.recurringEventId = b.recurringEventId;
        this.deleteMode = b.deleteMode;
    }

    public Provider getProvider() { return provider; }
    public String getCalendarId() { return calendarId; }
    public String getEventId() { return eventId; }
    /** ID of the recurring event series this instance belongs to. */
    public String getRecurringEventId() { return recurringEventId; }
    /** How to delete recurring instances: {@code "this"}, {@code "following"}, or {@code "all"}. */
    public String getDeleteMode() { return deleteMode; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Provider provider;
        private String calendarId;
        private String eventId;
        private String recurringEventId;
        private String deleteMode;

        public Builder provider(Provider v) { this.provider = v; return this; }
        public Builder calendarId(String v) { this.calendarId = v; return this; }
        public Builder eventId(String v) { this.eventId = v; return this; }
        public Builder recurringEventId(String v) { this.recurringEventId = v; return this; }
        public Builder deleteMode(String v) { this.deleteMode = v; return this; }

        public EventDeleteParams build() { return new EventDeleteParams(this); }
    }
}
