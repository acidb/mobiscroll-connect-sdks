package com.mobiscroll.connect.models;

import java.util.List;
import java.util.Objects;

import com.mobiscroll.connect.Provider;

/** Parameters for {@code Auth.generateAuthUrl}. */
public final class AuthUrlParams {

    private final String userId;
    private final String state;
    private final String scope;
    private final List<Provider> providers;

    private AuthUrlParams(Builder b) {
        this.userId = Objects.requireNonNull(b.userId, "userId");
        this.state = b.state;
        this.scope = b.scope;
        this.providers = b.providers;
    }

    public String getUserId() { return userId; }
    public String getState() { return state; }
    public String getScope() { return scope; }
    public List<Provider> getProviders() { return providers; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String userId;
        private String state;
        private String scope;
        private List<Provider> providers;

        public Builder userId(String v) { this.userId = v; return this; }
        public Builder state(String v) { this.state = v; return this; }
        public Builder scope(String v) { this.scope = v; return this; }
        public Builder providers(List<Provider> v) { this.providers = v; return this; }

        public AuthUrlParams build() { return new AuthUrlParams(this); }
    }
}
