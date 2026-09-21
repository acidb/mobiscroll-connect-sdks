package com.mobiscroll.connect.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result of {@code Webhooks.unsubscribeWebhook}. A {@code 200} response is final regardless of
 * {@link #getMessage()}: on a provider-side unsubscribe failure (e.g. an already-expired
 * subscription) the server still returns {@code success: true} with an explanatory message,
 * after removing the local mapping.
 */
public final class WebhookUnsubscribeResponse {

    private final boolean success;
    private final String message;

    @JsonCreator
    public WebhookUnsubscribeResponse(
            @JsonProperty("success") boolean success,
            @JsonProperty("message") String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    /** Explanatory message, present when the provider-side unsubscribe itself did not succeed. */
    public String getMessage() { return message; }
}
