package com.mobiscroll.connect;

import com.mobiscroll.connect.models.TokenResponse;
import com.mobiscroll.connect.resources.Auth;
import java.util.function.Consumer;
import com.mobiscroll.connect.resources.Calendars;
import com.mobiscroll.connect.resources.Events;

/**
 * Entry point for the Mobiscroll Connect SDK.
 *
 * <pre>
 * MobiscrollConnectClient client = new MobiscrollConnectClient(
 *         "your-client-id", "your-client-secret", "https://your-app.example/oauth/callback");
 * String url = client.auth().generateAuthUrl(
 *         AuthUrlParams.builder().userId("user-123").build());
 * </pre>
 */
public final class MobiscrollConnectClient {

    private final ApiClient api;
    private final Auth auth;
    private final Calendars calendars;
    private final Events events;

    public MobiscrollConnectClient(String clientId, String clientSecret, String redirectUri) {
        this(MobiscrollConnectConfig.builder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .redirectUri(redirectUri)
                .build());
    }

    public MobiscrollConnectClient(MobiscrollConnectConfig config) {
        this.api = new ApiClient(config);
        this.auth = new Auth(api);
        this.calendars = new Calendars(api);
        this.events = new Events(api);
    }

    public Auth auth() {
        return auth;
    }

    public Calendars calendars() {
        return calendars;
    }

    public Events events() {
        return events;
    }

    /** Set the tokens the SDK should use for subsequent authenticated calls. */
    public void setCredentials(TokenResponse tokens) {
        api.setCredentials(tokens);
    }

    /**
     * @return the currently cached credentials, or {@code null} if none are set.
     */
    public TokenResponse getCredentials() {
        return api.getCredentials();
    }

    /**
     * Override the token-refresh callback for this instance.
     * Useful in request-scoped web handlers where the persistence target changes
     * per request.
     */
    public void onTokensRefreshed(Consumer<TokenResponse> callback) {
        api.onTokensRefreshed(callback);
    }

    /** Internal — exposed for tests. */
    ApiClient apiClient() {
        return api;
    }
}
