package com.mobiscroll.connect.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** An event attendee returned by the API. */
public final class Attendee {

    private final String email;
    private final String status;
    private final Boolean organizer;

    @JsonCreator
    public Attendee(
            @JsonProperty("email") String email,
            @JsonProperty("status") String status,
            @JsonProperty("organizer") Boolean organizer) {
        this.email = email;
        this.status = status;
        this.organizer = organizer;
    }

    public String getEmail() { return email; }
    public String getStatus() { return status; }
    public Boolean getOrganizer() { return organizer; }
}
