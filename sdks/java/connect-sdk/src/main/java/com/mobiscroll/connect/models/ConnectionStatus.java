package com.mobiscroll.connect.models;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mobiscroll.connect.Provider;

/** Result of {@code Auth.getConnectionStatus} — connected accounts grouped by provider. */
public final class ConnectionStatus {

    private final Map<Provider, List<ConnectedAccount>> connections;
    private final boolean limitReached;

    @JsonCreator
    public ConnectionStatus(
            @JsonProperty("connections") Map<Provider, List<ConnectedAccount>> connections,
            @JsonProperty("limitReached") boolean limitReached) {
        this.connections = connections;
        this.limitReached = limitReached;
    }

    public Map<Provider, List<ConnectedAccount>> getConnections() { return connections; }
    public boolean isLimitReached() { return limitReached; }
}
