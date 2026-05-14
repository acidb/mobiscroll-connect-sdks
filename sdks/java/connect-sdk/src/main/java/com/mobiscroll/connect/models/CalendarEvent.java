package com.mobiscroll.connect.models;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.mobiscroll.connect.Provider;

/** A calendar event resource. */
public final class CalendarEvent {

    private final String id;
    private final Provider provider;
    private final String calendarId;
    private final String recurringEventId;
    private final String title;
    private final String location;
    private final OffsetDateTime start;
    private final OffsetDateTime end;
    private final Boolean allDay;
    private final String color;
    private final List<Attendee> attendees;
    private final Map<String, Object> custom;
    private final String conference;
    /** {@code "busy"} or {@code "free"}. */
    private final String availability;
    /** {@code "public"}, {@code "private"}, or {@code "confidential"}. */
    private final String privacy;
    /** {@code "confirmed"}, {@code "tentative"}, or {@code "cancelled"}. */
    private final String status;
    private final String link;
    private final JsonNode original;
    private final Map<String, Object> additional = new HashMap<>();

    @JsonCreator
    public CalendarEvent(
            @JsonProperty("id") String id,
            @JsonProperty("provider") Provider provider,
            @JsonProperty("calendarId") String calendarId,
            @JsonProperty("recurringEventId") String recurringEventId,
            @JsonProperty("title") String title,
            @JsonProperty("location") String location,
            @JsonProperty("start") OffsetDateTime start,
            @JsonProperty("end") OffsetDateTime end,
            @JsonProperty("allDay") Boolean allDay,
            @JsonProperty("color") String color,
            @JsonProperty("attendees") List<Attendee> attendees,
            @JsonProperty("custom") Map<String, Object> custom,
            @JsonProperty("conference") String conference,
            @JsonProperty("availability") String availability,
            @JsonProperty("privacy") String privacy,
            @JsonProperty("status") String status,
            @JsonProperty("link") String link,
            @JsonProperty("original") JsonNode original) {
        this.id = id;
        this.provider = provider;
        this.calendarId = calendarId;
        this.recurringEventId = recurringEventId;
        this.title = title;
        this.location = location;
        this.start = start;
        this.end = end;
        this.allDay = allDay;
        this.color = color;
        this.attendees = attendees;
        this.custom = custom;
        this.conference = conference;
        this.availability = availability;
        this.privacy = privacy;
        this.status = status;
        this.link = link;
        this.original = original;
    }

    public String getId() { return id; }
    public Provider getProvider() { return provider; }
    public String getCalendarId() { return calendarId; }
    /** ID of the recurring series this instance belongs to, if applicable. */
    public String getRecurringEventId() { return recurringEventId; }
    public String getTitle() { return title; }
    public String getLocation() { return location; }
    public OffsetDateTime getStart() { return start; }
    public OffsetDateTime getEnd() { return end; }
    public Boolean getAllDay() { return allDay; }
    public String getColor() { return color; }
    public List<Attendee> getAttendees() { return attendees; }
    public Map<String, Object> getCustom() { return custom; }
    public String getConference() { return conference; }
    public String getAvailability() { return availability; }
    public String getPrivacy() { return privacy; }
    public String getStatus() { return status; }
    public String getLink() { return link; }
    public JsonNode getOriginal() { return original; }

    @JsonAnyGetter
    public Map<String, Object> getAdditional() { return additional; }

    @JsonAnySetter
    public void setAdditional(String key, Object value) { additional.put(key, value); }
}
