package com.mobiscroll.connect.resources;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mobiscroll.connect.ApiClient;
import com.mobiscroll.connect.Provider;
import com.mobiscroll.connect.exceptions.NotFoundException;
import com.mobiscroll.connect.exceptions.ValidationException;
import com.mobiscroll.connect.internal.QueryStringBuilder;
import com.mobiscroll.connect.models.AuthUrlParams;
import com.mobiscroll.connect.models.ConnectionStatus;
import com.mobiscroll.connect.models.DisconnectParams;
import com.mobiscroll.connect.models.DisconnectResponse;
import com.mobiscroll.connect.models.RevokeResponse;
import com.mobiscroll.connect.models.TokenResponse;

/** OAuth resource: authorize URL, token exchange, connection status, disconnect, revoke. */
public final class Auth {

    private final ApiClient api;

    public Auth(ApiClient api) {
        this.api = api;
    }

    /** Build the OAuth consent URL the end user should be redirected to. */
    public String generateAuthUrl(AuthUrlParams params) {
        QueryStringBuilder qs = new QueryStringBuilder()
                .add("response_type", "code")
                .add("client_id", api.getConfig().getClientId())
                .add("redirect_uri", api.getConfig().getRedirectUri())
                .add("user_id", params.getUserId())
                .add("state", params.getState())
                .add("scope", params.getScope())
                .add("lng", params.getLng())
                .add("theme", params.getTheme());
        if (params.getProviders() != null) {
            for (Provider p : params.getProviders()) {
                qs.add("providers", p.wireValue());
            }
        }
        return api.getBaseUrl() + "/oauth/authorize?" + qs.build();
    }

    /** Exchange an authorization code for a token set. */
    public TokenResponse getToken(String code) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("redirect_uri", api.getConfig().getRedirectUri());
        TokenResponse tokens = api.postRaw("/oauth/token", form, new TypeReference<TokenResponse>() {});
        api.setCredentials(tokens);
        return tokens;
    }

    /** Cache a token set the SDK should use for subsequent requests. */
    public void setCredentials(TokenResponse tokens) {
        api.setCredentials(tokens);
    }

    /** List connected accounts per provider. Tries {@code /oauth/connection-status} then falls back to {@code /connection-status}. */
    public ConnectionStatus getConnectionStatus() {
        try {
            return api.get("/oauth/connection-status", null, new TypeReference<ConnectionStatus>() {});
        } catch (NotFoundException e) {
            return api.get("/connection-status", null, new TypeReference<ConnectionStatus>() {});
        }
    }

    /**
     * Disconnect a calendar provider account.
     *
     * <p>Removes the account (or, without {@code account}, every account of the provider) from
     * the user: stops its webhook subscriptions and deletes the stored provider credentials. It
     * does not revoke the user's Connect access or refresh tokens (see {@link #revoke()}), nor
     * the grant at Google or Microsoft. The {@code account} value is the {@code id} of an account
     * from {@link #getConnectionStatus()}. Falls back to {@code /disconnect} on 404.
     *
     * <pre>{@code
     * client.auth().disconnect(DisconnectParams.builder()
     *     .provider(Provider.GOOGLE).account("user@gmail.com").build());
     * client.auth().disconnect(DisconnectParams.builder().provider(Provider.MICROSOFT).build());
     * }</pre>
     */
    public DisconnectResponse disconnect(DisconnectParams params) {
        QueryStringBuilder qs = new QueryStringBuilder()
                .add("provider", params.getProvider().wireValue())
                .add("account", params.getAccount());
        Map<String, Object> empty = new LinkedHashMap<>();
        try {
            return api.post("/oauth/disconnect", qs.build(), empty, new TypeReference<DisconnectResponse>() {});
        } catch (NotFoundException e) {
            return api.post("/disconnect", qs.build(), empty, new TypeReference<DisconnectResponse>() {});
        }
    }

    /**
     * Revoke the stored Connect tokens: the refresh token if present, else the access token.
     * On success the stored credentials are cleared.
     *
     * @see #revoke(String)
     * @throws ValidationException when no credentials are stored (no request is made)
     */
    public RevokeResponse revoke() {
        TokenResponse stored = api.getCredentials();
        String token = null;
        if (stored != null) {
            token = stored.getRefreshToken() != null ? stored.getRefreshToken() : stored.getAccessToken();
        }
        if (token == null || token.isEmpty()) {
            throw new ValidationException("No token to revoke: pass one or set credentials first", null);
        }
        RevokeResponse response = send(token);
        api.clearCredentials();
        return response;
    }

    /**
     * Revoke the user's Connect access and refresh tokens.
     *
     * <p>Revokes the whole grant: every access and refresh token issued for this user. It does not
     * disconnect calendar accounts or stop webhook subscriptions. To remove a user entirely, call
     * {@link #disconnect(DisconnectParams)} for each provider first (it needs a valid access
     * token), then revoke. Succeeds for any token, including an unknown or already revoked one.
     * Passing a token leaves the stored credentials untouched; use {@link #revoke()} to revoke
     * and clear the stored ones.
     *
     * <pre>{@code
     * client.auth().disconnect(DisconnectParams.builder().provider(Provider.GOOGLE).build());
     * client.auth().revoke();
     *
     * // Or revoke a token you stored yourself
     * client.auth().revoke(storedRefreshToken);
     * }</pre>
     *
     * @param token an access or refresh token
     * @throws ValidationException when {@code token} is null or empty (no request is made)
     */
    public RevokeResponse revoke(String token) {
        if (token == null || token.isEmpty()) {
            throw new ValidationException("token is required", null);
        }
        return send(token);
    }

    private RevokeResponse send(String token) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", token);
        return api.postWithoutRefresh("/oauth/revoke", body, new TypeReference<RevokeResponse>() {});
    }
}
