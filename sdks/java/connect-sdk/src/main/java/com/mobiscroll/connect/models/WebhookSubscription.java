package com.mobiscroll.connect.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** The provider-side subscription created by {@code Webhooks.subscribeWebhook}. */
public final class WebhookSubscription {

    private final String channelId;
    private final String resourceId;
    private final String expiration;

    @JsonCreator
    public WebhookSubscription(
            @JsonProperty("channelId") String channelId,
            @JsonProperty("resourceId") String resourceId,
            @JsonProperty("expiration") String expiration) {
        this.channelId = channelId;
        this.resourceId = resourceId;
        this.expiration = expiration;
    }

    public String getChannelId() { return channelId; }
    /** Present for Google; {@code null} for providers that don't use it. */
    public String getResourceId() { return resourceId; }
    /** ISO 8601 expiration timestamp; present for some providers. */
    public String getExpiration() { return expiration; }
}
