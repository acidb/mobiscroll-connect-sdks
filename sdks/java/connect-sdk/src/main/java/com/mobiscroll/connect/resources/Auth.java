package com.mobiscroll.connect.resources;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mobiscroll.connect.ApiClient;
import com.mobiscroll.connect.Provider;
import com.mobiscroll.connect.exceptions.NotFoundException;
import com.mobiscroll.connect.internal.QueryStringBuilder;
import com.mobiscroll.connect.models.AuthUrlParams;
import com.mobiscroll.connect.models.ConnectionStatus;
import com.mobiscroll.connect.models.DisconnectParams;
import com.mobiscroll.connect.models.DisconnectResponse;
import com.mobiscroll.connect.models.TokenResponse;

/** OAuth resource: authorize URL, token exchange, connection status, disconnect. */
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
                .add("lng", params.getLng());
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

    /** Disconnect a provider (and optionally a specific account). */
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
}
