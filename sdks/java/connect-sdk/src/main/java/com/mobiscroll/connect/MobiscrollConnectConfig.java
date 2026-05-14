package com.mobiscroll.connect;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;

import com.mobiscroll.connect.models.TokenResponse;
import okhttp3.OkHttpClient;

/**
 * Immutable client configuration. Use {@link #builder()} for non-default base
 * URL, timeout, or callback.
 */
public final class MobiscrollConnectConfig {

    /** Default base URL for the Mobiscroll Connect API. */
    public static final String DEFAULT_BASE_URL = "https://connect.mobiscroll.com/api";
    /** Default HTTP timeout (30 seconds). */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String baseUrl;
    private final Duration timeout;
    private final OkHttpClient httpClient;
    private final Consumer<TokenResponse> onTokensRefreshed;

    private MobiscrollConnectConfig(Builder b) {
        this.clientId = require(b.clientId, "clientId");
        this.clientSecret = require(b.clientSecret, "clientSecret");
        this.redirectUri = require(b.redirectUri, "redirectUri");
        this.baseUrl = stripTrailingSlash(b.baseUrl != null ? b.baseUrl : DEFAULT_BASE_URL);
        this.timeout = b.timeout != null ? b.timeout : DEFAULT_TIMEOUT;
        this.httpClient = b.httpClient;
        this.onTokensRefreshed = b.onTokensRefreshed;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public OkHttpClient getHttpClient() {
        return httpClient;
    }

    public Consumer<TokenResponse> getOnTokensRefreshed() {
        return onTokensRefreshed;
    }

    public static Builder builder() {
        return new Builder();
    }

    private static String require(String v, String name) {
        if (v == null || v.isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return v;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public static final class Builder {
        private String clientId;
        private String clientSecret;
        private String redirectUri;
        private String baseUrl;
        private Duration timeout;
        private OkHttpClient httpClient;
        private Consumer<TokenResponse> onTokensRefreshed;

        public Builder clientId(String v) {
            this.clientId = v;
            return this;
        }

        public Builder clientSecret(String v) {
            this.clientSecret = v;
            return this;
        }

        public Builder redirectUri(String v) {
            this.redirectUri = v;
            return this;
        }

        public Builder baseUrl(String v) {
            this.baseUrl = v;
            return this;
        }

        public Builder timeout(Duration v) {
            this.timeout = v;
            return this;
        }

        public Builder httpClient(OkHttpClient v) {
            this.httpClient = v;
            return this;
        }

        public Builder onTokensRefreshed(Consumer<TokenResponse> v) {
            this.onTokensRefreshed = Objects.requireNonNull(v, "onTokensRefreshed");
            return this;
        }

        public MobiscrollConnectConfig build() {
            return new MobiscrollConnectConfig(this);
        }
    }
}
