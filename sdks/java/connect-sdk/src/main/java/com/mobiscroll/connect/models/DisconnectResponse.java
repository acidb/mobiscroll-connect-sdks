package com.mobiscroll.connect.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Result of a disconnect call. */
public final class DisconnectResponse {

    private final boolean success;

    @JsonCreator
    public DisconnectResponse(@JsonProperty("success") boolean success) {
        this.success = success;
    }

    public boolean isSuccess() { return success; }
}
