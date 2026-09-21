package com.mobiscroll.connect.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Result of {@code Webhooks.subscribeWebhook}. */
public final class WebhookSubscribeResponse {

    private final boolean success;
    private final String provider;
    private final WebhookSubscription subscription;
    private final String serverWebhookUrl;
    private final String channelId;

    @JsonCreator
    public WebhookSubscribeResponse(
            @JsonProperty("success") boolean success,
            @JsonProperty("provider") String provider,
            @JsonProperty("subscription") WebhookSubscription subscription,
            @JsonProperty("serverWebhookUrl") String serverWebhookUrl,
            @JsonProperty("channelId") String channelId) {
        this.success = success;
        this.provider = provider;
        this.subscription = subscription;
        this.serverWebhookUrl = serverWebhookUrl;
        this.channelId = channelId;
    }

    public boolean isSuccess() { return success; }
    public String getProvider() { return provider; }
    public WebhookSubscription getSubscription() { return subscription; }
    /** The Connect-hosted URL registered with the provider to receive change notifications. */
    public String getServerWebhookUrl() { return serverWebhookUrl; }
    /** Same value as {@link WebhookSubscription#getChannelId()}, surfaced at the top level for convenience. */
    public String getChannelId() { return channelId; }
}
