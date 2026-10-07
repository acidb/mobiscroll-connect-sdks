package com.mobiscroll.connect.exceptions;

/**
 * Thrown when a webhook delivery fails verification. Respond with a 4xx and do not process it.
 * {@link #getReason()} says why.
 */
public class WebhookVerificationException extends MobiscrollConnectException {

    private static final long serialVersionUID = 1L;

    /** Why a delivery failed verification. */
    public enum Reason {
        /** {@code webhook-id}, {@code webhook-timestamp} or {@code webhook-signature} is missing or empty. */
        MISSING_HEADERS("missing_headers"),
        /** {@code webhook-timestamp} is not a whole number of Unix seconds. */
        INVALID_TIMESTAMP("invalid_timestamp"),
        /** The timestamp is further from the current time than the tolerance allows. */
        TIMESTAMP_OUT_OF_TOLERANCE("timestamp_out_of_tolerance"),
        /** No usable public key was supplied, fetched or pinned. */
        NO_PUBLIC_KEYS("no_public_keys"),
        /** No {@code v1a} signature verified against any of the public keys. */
        NO_MATCHING_SIGNATURE("no_matching_signature"),
        /** The body is missing or is not a valid delivery JSON. */
        INVALID_PAYLOAD("invalid_payload");

        private final String wire;

        Reason(String wire) {
            this.wire = wire;
        }

        /** The snake_case name shared by every Mobiscroll Connect SDK, e.g. {@code no_matching_signature}. */
        public String wireValue() {
            return wire;
        }
    }

    private final Reason reason;

    public WebhookVerificationException(String message, Reason reason) {
        super(message);
        this.reason = reason;
    }

    /** Why the delivery failed verification. */
    public Reason getReason() {
        return reason;
    }
}
