package com.mobiscroll.connect.exceptions;

/** Thrown on HTTP 429. */
public class RateLimitException extends MobiscrollConnectException {

    private static final long serialVersionUID = 1L;

    private final Integer retryAfter;

    public RateLimitException(String message, Integer retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    /** Seconds parsed from the {@code Retry-After} response header, or {@code null} if absent or unparsable. */
    public Integer getRetryAfter() {
        return retryAfter;
    }
}
