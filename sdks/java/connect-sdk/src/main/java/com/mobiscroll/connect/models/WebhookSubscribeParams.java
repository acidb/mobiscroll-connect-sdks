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

    private WebhookSubscribeParams(Builder b) {
        this.provider = Objects.requireNonNull(b.provider, "provider");
        this.calendarId = Objects.requireNonNull(b.calendarId, "calendarId");
        this.channelId = b.channelId;
        this.expiration = b.expiration;
    }

    @JsonProperty("provider")    public Provider getProvider() { return provider; }
    @JsonProperty("calendarId")  public String getCalendarId() { return calendarId; }
    /** Client-supplied channel id. Auto-generated server-side if omitted. */
    @JsonProperty("channelId")   public String getChannelId() { return channelId; }
    /** Provider-specific expiration timestamp, milliseconds since epoch. */
    @JsonProperty("expiration")  public Long getExpiration() { return expiration; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Provider provider;
        private String calendarId;
        private String channelId;
        private Long expiration;

        public Builder provider(Provider v)     { this.provider = v; return this; }
        public Builder calendarId(String v)     { this.calendarId = v; return this; }
        public Builder channelId(String v)      { this.channelId = v; return this; }
        public Builder expiration(Long v)       { this.expiration = v; return this; }

        public WebhookSubscribeParams build() { return new WebhookSubscribeParams(this); }
    }
}
