package com.mobiscroll.connect.resources;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mobiscroll.connect.ApiClient;
import com.mobiscroll.connect.models.WebhookSubscribeParams;
import com.mobiscroll.connect.models.WebhookSubscribeResponse;
import com.mobiscroll.connect.models.WebhookUnsubscribeParams;
import com.mobiscroll.connect.models.WebhookUnsubscribeResponse;

/** Webhooks resource: subscribe/unsubscribe calendar change notifications. */
public final class Webhooks {

    private final ApiClient api;

    public Webhooks(ApiClient api) {
        this.api = api;
    }

    /** Subscribe to change notifications for a calendar. */
    public WebhookSubscribeResponse subscribeWebhook(WebhookSubscribeParams params) {
        return api.post("/subscribe-webhook", params, new TypeReference<WebhookSubscribeResponse>() {});
    }

    /**
     * Unsubscribe a previously created webhook channel. A {@code 200} response is final: on a
     * provider-side unsubscribe failure (e.g. an already-expired subscription) the server still
     * returns {@code success: true} with an explanatory message, after removing the local mapping.
     */
    public WebhookUnsubscribeResponse unsubscribeWebhook(WebhookUnsubscribeParams params) {
        return api.post("/unsubscribe-webhook", params, new TypeReference<WebhookUnsubscribeResponse>() {});
    }
}
