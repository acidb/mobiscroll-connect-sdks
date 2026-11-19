package com.mobiscroll.connect.models;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mobiscroll.connect.Provider;

/** Payload for {@code Webhooks.subscribeWebhook}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class WebhookSubscribeParams {

    private final Provider provider;
    private final String calendarId;
    private final String channelId;
    private final Long expiration;
    private final Boolean singleEvents;

    private WebhookSubscribeParams(Builder b) {
        this.provider = Objects.requireNonNull(b.provider, "provider");
        this.calendarId = Objects.requireNonNull(b.calendarId, "calendarId");
        this.channelId = b.channelId;
        this.expiration = b.expiration;
        this.singleEvents = b.singleEvents;
    }

    @JsonProperty("provider")    public Provider getProvider() { return provider; }
    @JsonProperty("calendarId")  public String getCalendarId() { return calendarId; }
    /** Client-supplied channel id. Auto-generated server-side if omitted. */
    @JsonProperty("channelId")   public String getChannelId() { return channelId; }
    /** Provider-specific expiration timestamp, milliseconds since epoch. */
    @JsonProperty("expiration")  public Long getExpiration() { return expiration; }
    /**
     * The channel's webhook mode, named like the {@code EventListParams} field. {@code false} is series
     * mode: for a Google calendar, a changed recurring series is delivered as its master event instead
     * of every occurrence. {@code true} delivers every occurrence. Outlook, iCloud and CalDAV deliver
     * the series master in both modes. When {@code null} it is left out of the request body, and the
     * channel keeps the mode of the subscription it replaces, otherwise the application's setting (set on
     * the dashboard) applies. A non-boolean value is rejected with a {@code ValidationException}.
     */
    @JsonProperty("singleEvents") public Boolean getSingleEvents() { return singleEvents; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Provider provider;
        private String calendarId;
        private String channelId;
        private Long expiration;
        private Boolean singleEvents;

        public Builder provider(Provider v)     { this.provider = v; return this; }
        public Builder calendarId(String v)     { this.calendarId = v; return this; }
        public Builder channelId(String v)      { this.channelId = v; return this; }
        public Builder expiration(Long v)       { this.expiration = v; return this; }
        public Builder singleEvents(Boolean v)  { this.singleEvents = v; return this; }

        public WebhookSubscribeParams build() { return new WebhookSubscribeParams(this); }
    }
}
