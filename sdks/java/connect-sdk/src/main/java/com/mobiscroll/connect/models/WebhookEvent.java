package com.mobiscroll.connect.models;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.mobiscroll.connect.Provider;

/** A changed calendar event in a {@link WebhookDelivery}. */
public final class WebhookEvent extends CalendarEvent {

    /** {@code "created"}, {@code "updated"} or {@code "deleted"}. */
    private final String changeType;

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
            @JsonProperty("conference") String conference,
            @JsonProperty("availability") String availability,
            @JsonProperty("privacy") String privacy,
            @JsonProperty("status") String status,
            @JsonProperty("link") String link,
            @JsonProperty("original") JsonNode original,
            @JsonProperty("description") String description,
            @JsonProperty("conferenceData") Map<String, Object> conferenceData,
            @JsonProperty("lastModified") String lastModified,
            @JsonProperty("changeType") String changeType) {
        super(id, provider, calendarId, recurringEventId, title, location, start, end, allDay, color, attendees,
                custom, conference, availability, privacy, status, link, original, description, conferenceData,
                lastModified);
        this.changeType = changeType;
    }

    /** {@code "created"}, {@code "updated"} or {@code "deleted"}. */
    public String getChangeType() { return changeType; }
}
