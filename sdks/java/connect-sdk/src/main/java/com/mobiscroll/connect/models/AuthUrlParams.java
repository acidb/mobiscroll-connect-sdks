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
    private final String lng;
    private final String theme;

    private AuthUrlParams(Builder b) {
        this.userId = Objects.requireNonNull(b.userId, "userId");
        this.state = b.state;
        this.scope = b.scope;
        this.providers = b.providers;
        this.lng = b.lng;
        this.theme = b.theme;
    }

    public String getUserId() { return userId; }
    public String getState() { return state; }
    public String getScope() { return scope; }
    public List<Provider> getProviders() { return providers; }

    /**
     * Language code for the Connect authorization pages, e.g. {@code "es"}; null falls back to
     * Accept-Language then English.
     *
     * @see <a href="https://mobiscroll.com/docs/connect/localization#supported-languages">Supported languages</a>
     */
    public String getLng() { return lng; }

    /**
     * Color scheme of the Connect authorization pages: {@code "light"}, {@code "dark"} or {@code "auto"}
     * (follows the device setting); null applies the project's default theme.
     */
    public String getTheme() { return theme; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String userId;
        private String state;
        private String scope;
        private List<Provider> providers;
        private String lng;
        private String theme;

        public Builder userId(String v) { this.userId = v; return this; }
        public Builder state(String v) { this.state = v; return this; }
        public Builder scope(String v) { this.scope = v; return this; }
        public Builder providers(List<Provider> v) { this.providers = v; return this; }
        public Builder lng(String v) { this.lng = v; return this; }
        public Builder theme(String v) { this.theme = v; return this; }

        public AuthUrlParams build() { return new AuthUrlParams(this); }
    }
}
