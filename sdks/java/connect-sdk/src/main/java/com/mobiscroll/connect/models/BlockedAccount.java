package com.mobiscroll.connect.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** A connected account that withheld calendar access on its provider's consent screen. */
public final class BlockedAccount {

    private final String provider;
    private final String account;

    @JsonCreator
    public BlockedAccount(
            @JsonProperty("provider") String provider,
            @JsonProperty("account") String account) {
        this.provider = provider;
        this.account = account;
    }

    /** Lowercase provider name, e.g. {@code "google"}. */
    public String getProvider() { return provider; }
    /** Account identifier, usually an email address. */
    public String getAccount() { return account; }
}
