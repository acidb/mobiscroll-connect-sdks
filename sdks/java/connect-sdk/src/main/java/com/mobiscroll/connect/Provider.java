package com.mobiscroll.connect;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Calendar provider supported by the Mobiscroll Connect API. */
public enum Provider {
    GOOGLE("google"),
    MICROSOFT("microsoft"),
    APPLE("apple"),
    CALDAV("caldav");

    private final String wire;

    Provider(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wireValue() {
        return wire;
    }

    public static Provider fromWire(String value) {
        if (value == null) {
            return null;
        }
        for (Provider p : values()) {
            if (p.wire.equalsIgnoreCase(value)) {
                return p;
            }
        }
        throw new IllegalArgumentException("Unknown provider: " + value);
    }

    /**
     * Reads a provider from an API response or a webhook delivery. A value this SDK version does not
     * know, sent by a newer Connect, is read as {@code null} instead of failing the whole response.
     */
    @JsonCreator
    static Provider fromJson(String value) {
        if (value == null) {
            return null;
        }
        for (Provider p : values()) {
            if (p.wire.equalsIgnoreCase(value)) {
                return p;
            }
        }
        return null;
    }
}
