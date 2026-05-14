package com.mobiscroll.connect.models;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mobiscroll.connect.Provider;

/** Payload for {@code Events.update}. Includes the event id; other fields are optional patches. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class EventUpdateData {

    private final Provider provider;
    private final String calendarId;
    private final String eventId;
    private final String recurringEventId;
    private final String updateMode;
    private final String title;
    private final String description;
    private final String location;
    private final OffsetDateTime start;
    private final OffsetDateTime end;
    private final Boolean allDay;
    private final List<String> attendees;
    private final RecurrenceRule recurrence;
    private final Map<String, Object> custom;
    private final String availability;
    private final String privacy;
    private final String status;

    private EventUpdateData(Builder b) {
        this.provider = Objects.requireNonNull(b.provider, "provider");
        this.calendarId = Objects.requireNonNull(b.calendarId, "calendarId");
        this.eventId = Objects.requireNonNull(b.eventId, "eventId");
        this.recurringEventId = b.recurringEventId;
        this.updateMode = b.updateMode;
        this.title = b.title;
        this.description = b.description;
        this.location = b.location;
        this.start = b.start;
        this.end = b.end;
        this.allDay = b.allDay;
        this.attendees = b.attendees;
        this.recurrence = b.recurrence;
        this.custom = b.custom;
        this.availability = b.availability;
        this.privacy = b.privacy;
        this.status = b.status;
    }

    @JsonProperty("provider")         public Provider getProvider() { return provider; }
    @JsonProperty("calendarId")       public String getCalendarId() { return calendarId; }
    @JsonProperty("eventId")          public String getEventId() { return eventId; }
    /** ID of the recurring event series this instance belongs to. */
    @JsonProperty("recurringEventId") public String getRecurringEventId() { return recurringEventId; }
    /** How to update recurring instances: {@code "this"}, {@code "following"}, or {@code "all"}. */
    @JsonProperty("updateMode")       public String getUpdateMode() { return updateMode; }
    @JsonProperty("title")            public String getTitle() { return title; }
    @JsonProperty("description")      public String getDescription() { return description; }
    @JsonProperty("location")         public String getLocation() { return location; }
    @JsonProperty("start")            public OffsetDateTime getStart() { return start; }
    @JsonProperty("end")              public OffsetDateTime getEnd() { return end; }
    @JsonProperty("allDay")           public Boolean getAllDay() { return allDay; }
    @JsonProperty("attendees")        public List<String> getAttendees() { return attendees; }
    @JsonProperty("recurrence")       public RecurrenceRule getRecurrence() { return recurrence; }
    @JsonProperty("custom")           public Map<String, Object> getCustom() { return custom; }
    @JsonProperty("availability")     public String getAvailability() { return availability; }
    @JsonProperty("privacy")          public String getPrivacy() { return privacy; }
    @JsonProperty("status")           public String getStatus() { return status; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Provider provider;
        private String calendarId;
        private String eventId;
        private String recurringEventId;
        private String updateMode;
        private String title;
        private String description;
        private String location;
        private OffsetDateTime start;
        private OffsetDateTime end;
        private Boolean allDay;
        private List<String> attendees;
        private RecurrenceRule recurrence;
        private Map<String, Object> custom;
        private String availability;
        private String privacy;
        private String status;

        public Builder provider(Provider v)           { this.provider = v; return this; }
        public Builder calendarId(String v)           { this.calendarId = v; return this; }
        public Builder eventId(String v)              { this.eventId = v; return this; }
        public Builder recurringEventId(String v)     { this.recurringEventId = v; return this; }
        public Builder updateMode(String v)           { this.updateMode = v; return this; }
        public Builder title(String v)                { this.title = v; return this; }
        public Builder description(String v)          { this.description = v; return this; }
        public Builder location(String v)             { this.location = v; return this; }
        public Builder start(OffsetDateTime v)        { this.start = v; return this; }
        public Builder end(OffsetDateTime v)          { this.end = v; return this; }
        public Builder allDay(Boolean v)              { this.allDay = v; return this; }
        public Builder attendees(List<String> v)      { this.attendees = v; return this; }
        public Builder recurrence(RecurrenceRule v)   { this.recurrence = v; return this; }
        public Builder custom(Map<String, Object> v)  { this.custom = v; return this; }
        public Builder availability(String v)         { this.availability = v; return this; }
        public Builder privacy(String v)              { this.privacy = v; return this; }
        public Builder status(String v)               { this.status = v; return this; }

        public EventUpdateData build() { return new EventUpdateData(this); }
    }
}
