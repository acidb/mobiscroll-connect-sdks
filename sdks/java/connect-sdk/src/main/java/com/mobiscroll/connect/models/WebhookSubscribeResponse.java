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
    private final Boolean singleEvents;

    @JsonCreator
    public WebhookSubscribeResponse(
            @JsonProperty("success") boolean success,
            @JsonProperty("provider") String provider,
            @JsonProperty("subscription") WebhookSubscription subscription,
            @JsonProperty("serverWebhookUrl") String serverWebhookUrl,
            @JsonProperty("channelId") String channelId,
            @JsonProperty("singleEvents") Boolean singleEvents) {
        this.success = success;
        this.provider = provider;
        this.subscription = subscription;
        this.serverWebhookUrl = serverWebhookUrl;
        this.channelId = channelId;
        this.singleEvents = singleEvents;
    }

    public WebhookSubscribeResponse(boolean success, String provider, WebhookSubscription subscription,
            String serverWebhookUrl, String channelId) {
        this(success, provider, subscription, serverWebhookUrl, channelId, null);
    }

    public boolean isSuccess() { return success; }
    public String getProvider() { return provider; }
    public WebhookSubscription getSubscription() { return subscription; }
    /** The Connect-hosted URL registered with the provider to receive change notifications. */
    public String getServerWebhookUrl() { return serverWebhookUrl; }
    /** Same value as {@link WebhookSubscription#getChannelId()}, surfaced at the top level for convenience. */
    public String getChannelId() { return channelId; }
    /**
     * The mode the channel was created with: {@code false} is series mode, {@code true} is every
     * occurrence. {@code null} when the response does not carry it, which means unknown, not series mode.
     */
    public Boolean getSingleEvents() { return singleEvents; }
}
