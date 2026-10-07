package com.mobiscroll.connect.resources;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mobiscroll.connect.ApiClient;
import com.mobiscroll.connect.WebhookVerifier;
import com.mobiscroll.connect.exceptions.MobiscrollConnectException;
import com.mobiscroll.connect.exceptions.WebhookVerificationException;
import com.mobiscroll.connect.exceptions.WebhookVerificationException.Reason;
import com.mobiscroll.connect.internal.JsonMapperHolder;
import com.mobiscroll.connect.internal.WebhookKeyStore;
import com.mobiscroll.connect.models.VerifyWebhookSignatureOptions;
import com.mobiscroll.connect.models.WebhookDelivery;
import com.mobiscroll.connect.models.WebhookSubscribeParams;
import com.mobiscroll.connect.models.WebhookSubscribeResponse;
import com.mobiscroll.connect.models.WebhookUnsubscribeParams;
import com.mobiscroll.connect.models.WebhookUnsubscribeResponse;
import okhttp3.HttpUrl;

/** Webhooks resource: subscribe/unsubscribe calendar change notifications and verify deliveries. */
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

    /**
     * Same as {@link #verifyWebhook(byte[], Map)} for a body held as a string; it is encoded as UTF-8.
     * Prefer the {@code byte[]} overload when the framework gives you the bytes.
     */
    public WebhookDelivery verifyWebhook(String payload, Map<String, ?> headers) {
        if (payload == null) {
            throw new WebhookVerificationException("Pass the raw request body", Reason.INVALID_PAYLOAD);
        }
        return verifyWebhook(payload.getBytes(StandardCharsets.UTF_8), headers);
    }

    /**
     * Verify that a webhook delivery came from Mobiscroll Connect, and parse it.
     *
     * <p>Fetches the public keys from {@code /.well-known/webhook-keys} on first use and caches them for
     * the whole process, refreshing them as the endpoint's {@code Cache-Control} allows. When no
     * signature matches, it re-fetches the keys once (at most once a minute) before rejecting, so a key
     * rotation never rejects genuine deliveries. The configured {@code webhookPublicKey} is used only
     * when the endpoint cannot be reached. Blocks the calling thread while the keys are fetched.
     *
     * <pre>
     * &#64;PostMapping("/webhooks/mobiscroll")
     * ResponseEntity&lt;Void&gt; webhook(&#64;RequestBody byte[] body, &#64;RequestHeader Map&lt;String, String&gt; headers) {
     *     WebhookDelivery delivery;
     *     try {
     *         delivery = client.webhooks().verifyWebhook(body, headers);
     *     } catch (WebhookVerificationException e) {
     *         return ResponseEntity.status(401).build();
     *     }
     *     handleDelivery(delivery);
     *     return ResponseEntity.noContent().build();
     * }
     * </pre>
     *
     * @param payload the raw request body, exactly as received
     * @param headers the request headers, looked up case-insensitively; values may be {@code String}s or
     *                collections of them, so both {@code Map<String, String>} and
     *                {@code Map<String, List<String>>} work
     * @return the parsed delivery
     * @throws WebhookVerificationException when the delivery is not genuine; respond with a 4xx
     */
    public WebhookDelivery verifyWebhook(byte[] payload, Map<String, ?> headers) {
        WebhookKeyStore store = WebhookKeyStore.forUrl(keysUrl());
        String pinnedKey = api.getConfig().getWebhookPublicKey();

        try {
            verify(payload, headers, keysOrPinned(store.getKeys(), pinnedKey));
        } catch (WebhookVerificationException e) {
            boolean retryable = e.getReason() == Reason.NO_MATCHING_SIGNATURE
                    || e.getReason() == Reason.NO_PUBLIC_KEYS;
            if (!retryable || !store.canRefetch()) {
                throw e;
            }
            store.refresh();
            verify(payload, headers, keysOrPinned(store.getKeys(), pinnedKey));
        }

        try {
            WebhookDelivery delivery = JsonMapperHolder.get().readValue(payload, WebhookDelivery.class);
            if (delivery == null) {
                throw new WebhookVerificationException("Webhook payload is empty", Reason.INVALID_PAYLOAD);
            }
            return delivery;
        } catch (IOException e) {
            throw new WebhookVerificationException("Webhook payload is not valid JSON", Reason.INVALID_PAYLOAD);
        }
    }

    private static void verify(byte[] payload, Map<String, ?> headers, List<String> keys) {
        WebhookVerifier.verifyWebhookSignature(payload, headers, keys, VerifyWebhookSignatureOptions.builder()
                .now(WebhookKeyStore.currentTimeMillis() / 1000)
                .build());
    }

    private static List<String> keysOrPinned(List<String> keys, String pinnedKey) {
        return keys.isEmpty() && pinnedKey != null && !pinnedKey.isEmpty() ? List.of(pinnedKey) : keys;
    }

    private String keysUrl() {
        HttpUrl base = HttpUrl.parse(api.getBaseUrl());
        if (base == null) {
            throw new MobiscrollConnectException("Invalid base URL: " + api.getBaseUrl());
        }
        return base.newBuilder()
                .encodedPath(WebhookKeyStore.KEYS_PATH)
                .query(null)
                .fragment(null)
                .build()
                .toString();
    }
}
