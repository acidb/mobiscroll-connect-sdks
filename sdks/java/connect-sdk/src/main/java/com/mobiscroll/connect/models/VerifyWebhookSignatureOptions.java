package com.mobiscroll.connect.models;

/** Options for {@code WebhookVerifier.verifyWebhookSignature}. */
public final class VerifyWebhookSignatureOptions {

    /** Default allowed distance between the delivery timestamp and the current time, in seconds. */
    public static final long DEFAULT_TOLERANCE_SECONDS = 300;

    private final long toleranceSeconds;
    private final Long now;

    private VerifyWebhookSignatureOptions(Builder b) {
        this.toleranceSeconds = b.toleranceSeconds;
        this.now = b.now;
    }

    /** Allowed distance between the {@code webhook-timestamp} header and {@link #getNow()}, in seconds. */
    public long getToleranceSeconds() { return toleranceSeconds; }
    /** Current time in Unix seconds to check the timestamp against, or {@code null} for the system clock. */
    public Long getNow() { return now; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private long toleranceSeconds = DEFAULT_TOLERANCE_SECONDS;
        private Long now;

        public Builder toleranceSeconds(long v) { this.toleranceSeconds = v; return this; }
        /** Unix seconds; for tests. Defaults to the system clock. */
        public Builder now(Long v)              { this.now = v; return this; }

        public VerifyWebhookSignatureOptions build() { return new VerifyWebhookSignatureOptions(this); }
    }
}
