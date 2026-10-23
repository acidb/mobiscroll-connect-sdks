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

/**
 * A changed calendar event in a {@link WebhookDelivery}. It has the fields of {@link CalendarEvent}, except
 * that {@link #getConference()} is a {@link WebhookConference} object, as deliveries carry it.
 *
 * <p>Deleted Microsoft, Apple and CalDAV events arrive as placeholders without {@code calendarId} and
 * {@code original}; rely only on {@link #getId()}, {@link #getProvider()} and {@link #getChangeType()} for a
 * deleted event.
 */
public final class WebhookEvent {

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
    private final WebhookConference conference;
    private final String availability;
    private final String privacy;
    private final String status;
    private final String link;
    private final JsonNode original;
    private final String description;
    private final Map<String, Object> conferenceData;
    private final String lastModified;
    private final String changeType;
    private final Map<String, Object> additional = new HashMap<>();

    @JsonCreator
    public WebhookEvent(
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
            @JsonProperty("conference") WebhookConference conference,
            @JsonProperty("availability") String availability,
            @JsonProperty("privacy") String privacy,
            @JsonProperty("status") String status,
            @JsonProperty("link") String link,
            @JsonProperty("original") JsonNode original,
            @JsonProperty("description") String description,
            @JsonProperty("conferenceData") Map<String, Object> conferenceData,
            @JsonProperty("lastModified") String lastModified,
            @JsonProperty("changeType") String changeType) {
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
        this.description = description;
        this.conferenceData = conferenceData;
        this.lastModified = lastModified;
        this.changeType = changeType;
    }

    public String getId() { return id; }
    public Provider getProvider() { return provider; }
    /** {@code null} on deleted placeholders. */
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
    /** Conference details; {@code null} when the event has none. */
    public WebhookConference getConference() { return conference; }
    /** {@code "busy"} or {@code "free"}. */
    public String getAvailability() { return availability; }
    /** {@code "public"}, {@code "private"}, or {@code "confidential"}. */
    public String getPrivacy() { return privacy; }
    /** {@code "confirmed"}, {@code "tentative"}, or {@code "cancelled"}. */
    public String getStatus() { return status; }
    public String getLink() { return link; }
    /** Raw provider event payload; {@code null} on deleted placeholders. */
    public JsonNode getOriginal() { return original; }
    /** Event description or notes. */
    public String getDescription() { return description; }
    /** Provider-specific conference metadata; keys vary per provider. */
    public Map<String, Object> getConferenceData() { return conferenceData; }
    /** ISO 8601 timestamp of the last modification; for a deleted placeholder, when the deletion was detected. */
    public String getLastModified() { return lastModified; }
    /** {@code "created"}, {@code "updated"} or {@code "deleted"}. */
    public String getChangeType() { return changeType; }

    @JsonAnyGetter
    public Map<String, Object> getAdditional() { return additional; }

    @JsonAnySetter
    public void setAdditional(String key, Object value) { additional.put(key, value); }
}
