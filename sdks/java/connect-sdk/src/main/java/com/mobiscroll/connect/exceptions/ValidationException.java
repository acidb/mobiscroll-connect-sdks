package com.mobiscroll.connect.exceptions;

import com.fasterxml.jackson.databind.JsonNode;

/** Thrown on HTTP 400 / 422 — request payload was rejected by the server. */
public class ValidationException extends MobiscrollConnectException {

    private static final long serialVersionUID = 1L;

    private final transient JsonNode details;

    public ValidationException(String message, JsonNode details) {
        super(message);
        this.details = details;
    }

    /** Raw {@code details} field from the server error body, or {@code null} if not present. */
    public JsonNode getDetails() {
        return details;
    }
}
