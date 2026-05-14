package com.mobiscroll.connect.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** OAuth token response returned from the token-exchange and refresh endpoints. */
public final class TokenResponse {

    private final String accessToken;
    private final String tokenType;
    private final Integer expiresIn;
    private final String refreshToken;
    private final String scope;

    @JsonCreator
    public TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") Integer expiresIn,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("scope") String scope) {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
        this.refreshToken = refreshToken;
        this.scope = scope;
    }

    @JsonProperty("access_token")
    public String getAccessToken() { return accessToken; }

    @JsonProperty("token_type")
    public String getTokenType() { return tokenType; }

    @JsonProperty("expires_in")
    public Integer getExpiresIn() { return expiresIn; }

    @JsonProperty("refresh_token")
    public String getRefreshToken() { return refreshToken; }

    public String getScope() { return scope; }

    /**
     * Merge {@code incoming} into this token, preserving the existing refresh token if the server
     * did not issue a new one. Used by the refresh flow.
     */
    public TokenResponse mergedWith(TokenResponse incoming) {
        String newRefresh = incoming.refreshToken != null && !incoming.refreshToken.isEmpty()
                ? incoming.refreshToken
                : this.refreshToken;
        return new TokenResponse(
                incoming.accessToken != null ? incoming.accessToken : this.accessToken,
                incoming.tokenType != null ? incoming.tokenType : this.tokenType,
                incoming.expiresIn != null ? incoming.expiresIn : this.expiresIn,
                newRefresh,
                incoming.scope != null ? incoming.scope : this.scope);
    }
}
