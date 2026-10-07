package com.mobiscroll.connect.models;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mobiscroll.connect.Provider;

/** A verified webhook delivery sent by Mobiscroll Connect to the project's webhook URL. */
public final class WebhookDelivery {

    private final Provider provider;
    private final String userId;
    private final String calendarId;
    private final List<WebhookEvent> events;
    private final String changeType;
    private final String timestamp;
    private final Metadata metadata;

    @JsonCreator
    public WebhookDelivery(
            @JsonProperty("provider") Provider provider,
            @JsonProperty("userId") String userId,
            @JsonProperty("calendarId") String calendarId,
            @JsonProperty("events") List<WebhookEvent> events,
            @JsonProperty("changeType") String changeType,
            @JsonProperty("timestamp") String timestamp,
            @JsonProperty("metadata") Metadata metadata) {
        this.provider = provider;
        this.userId = userId;
        this.calendarId = calendarId;
        this.events = events;
        this.changeType = changeType;
        this.timestamp = timestamp;
        this.metadata = metadata;
    }

    public Provider getProvider() { return provider; }
    /** Your user id, as passed when the user connected. */
    public String getUserId() { return userId; }
    public String getCalendarId() { return calendarId; }
    public List<WebhookEvent> getEvents() { return events; }
    /** {@code "created"}, {@code "updated"}, {@code "deleted"} or {@code "mixed"}; may be {@code null}. */
    public String getChangeType() { return changeType; }
    /** ISO 8601 timestamp of when Connect processed the change. */
    public String getTimestamp() { return timestamp; }
    public Metadata getMetadata() { return metadata; }

    /** Delivery metadata. */
    public static final class Metadata {

        private final String channelId;
        private final int eventCount;
        private final Boolean isInitialSync;

        @JsonCreator
        public Metadata(
                @JsonProperty("channelId") String channelId,
                @JsonProperty("eventCount") int eventCount,
                @JsonProperty("isInitialSync") Boolean isInitialSync) {
            this.channelId = channelId;
            this.eventCount = eventCount;
            this.isInitialSync = isInitialSync;
        }

        /** The webhook channel the delivery belongs to. */
        public String getChannelId() { return channelId; }
        public int getEventCount() { return eventCount; }
        /** {@code true} for the deliveries sent while the calendar is first synchronised; may be {@code null}. */
        public Boolean isInitialSync() { return isInitialSync; }
    }
}
