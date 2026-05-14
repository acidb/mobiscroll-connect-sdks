package com.mobiscroll.connect.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** A connected calendar account (e.g. a Google or Microsoft user). */
public final class ConnectedAccount {

    private final String id;
    private final String display;

    @JsonCreator
    public ConnectedAccount(
            @JsonProperty("id") String id,
            @JsonProperty("display") String display) {
        this.id = id;
        this.display = display;
    }

    /** Account identifier, usually an email address. */
    public String getId() { return id; }
    /** Display name, if provided by the server. */
    public String getDisplay() { return display; }
}
