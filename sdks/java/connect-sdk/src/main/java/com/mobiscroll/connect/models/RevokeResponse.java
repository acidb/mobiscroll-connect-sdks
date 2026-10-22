package com.mobiscroll.connect.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Result of a revoke call. */
public final class RevokeResponse {

    private final boolean success;

    @JsonCreator
    public RevokeResponse(@JsonProperty("success") boolean success) {
        this.success = success;
    }

    public boolean isSuccess() { return success; }
}
