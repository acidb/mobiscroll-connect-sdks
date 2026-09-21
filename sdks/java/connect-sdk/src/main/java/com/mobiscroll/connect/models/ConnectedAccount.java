package com.mobiscroll.connect.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/** A connected calendar account (e.g. a Google or Microsoft user). */
public final class ConnectedAccount {

    private final String id;
    private final String display;
    private final List<String> grantedScopes;
    private final Boolean calendarPermissionGranted;
    private final String syncState;
    private final String syncStateUpdatedAt;

    @JsonCreator
    public ConnectedAccount(
            @JsonProperty("id") String id,
            @JsonProperty("display") String display,
            @JsonProperty("grantedScopes") List<String> grantedScopes,
            @JsonProperty("calendarPermissionGranted") Boolean calendarPermissionGranted,
            @JsonProperty("syncState") String syncState,
            @JsonProperty("syncStateUpdatedAt") String syncStateUpdatedAt) {
        this.id = id;
        this.display = display;
        this.grantedScopes = grantedScopes == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(grantedScopes);
        this.calendarPermissionGranted = calendarPermissionGranted;
        this.syncState = syncState == null ? "active" : syncState;
        this.syncStateUpdatedAt = syncStateUpdatedAt;
    }

    /** Account identifier, usually an email address. */
    public String getId() { return id; }
    /** Display name, if provided by the server. */
    public String getDisplay() { return display; }

    /**
     * Scopes the provider actually granted for this account — not necessarily the ones
     * Connect asked for. Google's consent screen lets the user untick the calendar
     * permission and still complete sign-in. Empty for Apple and CalDav, which
     * authenticate with a username and app password.
     */
    public List<String> getGrantedScopes() { return grantedScopes; }

    /**
     * Whether this account granted calendar access sufficient for your project's scope.
     * {@code FALSE} means the account is connected but no calendars can be read from it
     * until the user reconnects and allows access. {@code null} means the question does
     * not apply (Apple, CalDav) or no scopes were recorded for the account.
     */
    public Boolean getCalendarPermissionGranted() { return calendarPermissionGranted; }

    /**
     * Whether the stored credentials for this account still work: {@code "active"}, or
     * {@code "reauth_required"} once the provider has rejected them. This answers a
     * different question from {@link #getCalendarPermissionGranted()}, which records what
     * was agreed at consent time and never changes afterwards — so it cannot report a
     * grant the user revoked later. Neither is repairable server-side; the user must run
     * the connect flow again.
     */
    public String getSyncState() { return syncState; }

    /** ISO 8601 time {@code syncState} last changed, or {@code null} if it never has. */
    public String getSyncStateUpdatedAt() { return syncStateUpdatedAt; }
}
