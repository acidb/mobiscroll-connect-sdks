package com.mobiscroll.connect.models;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mobiscroll.connect.Provider;

/** Payload for {@code Webhooks.unsubscribeWebhook}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class WebhookUnsubscribeParams {

    private final Provider provider;
    private final String channelId;
    private final String resourceId;

    private WebhookUnsubscribeParams(Builder b) {
        this.provider = Objects.requireNonNull(b.provider, "provider");
        this.channelId = Objects.requireNonNull(b.channelId, "channelId");
        this.resourceId = b.resourceId;
    }

    @JsonProperty("provider")   public Provider getProvider() { return provider; }
    @JsonProperty("channelId")  public String getChannelId() { return channelId; }
    /** Required by some providers (e.g. Google) to fully unsubscribe the channel. */
    @JsonProperty("resourceId") public String getResourceId() { return resourceId; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Provider provider;
        private String channelId;
        private String resourceId;

        public Builder provider(Provider v)    { this.provider = v; return this; }
        public Builder channelId(String v)     { this.channelId = v; return this; }
        public Builder resourceId(String v)    { this.resourceId = v; return this; }

        public WebhookUnsubscribeParams build() { return new WebhookUnsubscribeParams(this); }
    }
}
