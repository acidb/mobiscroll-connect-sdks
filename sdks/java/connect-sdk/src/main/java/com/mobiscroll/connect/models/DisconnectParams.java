package com.mobiscroll.connect.models;

import java.util.Objects;

import com.mobiscroll.connect.Provider;

/** Parameters for {@code Auth.disconnect}. */
public final class DisconnectParams {

    private final Provider provider;
    private final String account;

    private DisconnectParams(Builder b) {
        this.provider = Objects.requireNonNull(b.provider, "provider");
        this.account = b.account;
    }

    public Provider getProvider() { return provider; }
    public String getAccount() { return account; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Provider provider;
        private String account;

        public Builder provider(Provider v) { this.provider = v; return this; }
        public Builder account(String v) { this.account = v; return this; }

        public DisconnectParams build() { return new DisconnectParams(this); }
    }
}
