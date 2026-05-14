package com.mobiscroll.connect;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobiscroll.connect.exceptions.AuthenticationException;
import com.mobiscroll.connect.exceptions.MobiscrollConnectException;
import com.mobiscroll.connect.exceptions.NetworkException;
import com.mobiscroll.connect.exceptions.NotFoundException;
import com.mobiscroll.connect.exceptions.RateLimitException;
import com.mobiscroll.connect.exceptions.ServerException;
import com.mobiscroll.connect.exceptions.ValidationException;
import com.mobiscroll.connect.internal.JsonMapperHolder;
import com.mobiscroll.connect.models.TokenResponse;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Internal HTTP transport. Injects {@code Authorization: Bearer} on each
 * request, intercepts 401
 * responses to refresh the access token (deduplicated across concurrent
 * callers) and retry once,
 * and maps HTTP error responses to typed exceptions.
 */
public final class ApiClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final MediaType FORM = MediaType.get("application/x-www-form-urlencoded");

    private final MobiscrollConnectConfig config;
    private final OkHttpClient http;
    private final ObjectMapper json;
    private final AtomicReference<CompletableFuture<TokenResponse>> inflight = new AtomicReference<>();

    private volatile TokenResponse credentials;
    private volatile Consumer<TokenResponse> onTokensRefreshedOverride;

    public ApiClient(MobiscrollConnectConfig config) {
        this.config = config;
        this.json = JsonMapperHolder.get();
        OkHttpClient base = config.getHttpClient();
        if (base == null) {
            this.http = new OkHttpClient.Builder()
                    .callTimeout(config.getTimeout())
                    .build();
        } else {
            this.http = base.newBuilder()
                    .callTimeout(config.getTimeout())
                    .build();
        }
    }

    public String getBaseUrl() {
        return config.getBaseUrl();
    }

    public MobiscrollConnectConfig getConfig() {
        return config;
    }

    public void setCredentials(TokenResponse tokens) {
        this.credentials = tokens;
    }

    public TokenResponse getCredentials() {
        return credentials;
    }

    /**
     * Override the token-refresh callback for this client instance (e.g.
     * per-request in web apps).
     */
    public void onTokensRefreshed(Consumer<TokenResponse> callback) {
        this.onTokensRefreshedOverride = callback;
    }

    public <T> T get(String path, String query, TypeReference<T> type) {
        return execute(buildRequest("GET", path, query, null), type, /* allowRetry */ true);
    }

    public <T> T post(String path, Object body, TypeReference<T> type) {
        return execute(buildRequest("POST", path, null, jsonBody(body)), type, true);
    }

    public <T> T post(String path, String query, Object body, TypeReference<T> type) {
        return execute(buildRequest("POST", path, query, jsonBody(body)), type, true);
    }

    public <T> T put(String path, Object body, TypeReference<T> type) {
        return execute(buildRequest("PUT", path, null, jsonBody(body)), type, true);
    }

    public void delete(String path, String query) {
        execute(buildRequest("DELETE", path, query, null), null, true);
    }

    public <T> T deleteForResult(String path, String query, TypeReference<T> type) {
        return execute(buildRequest("DELETE", path, query, null), type, true);
    }

    /**
     * Used by the OAuth flow itself (token exchange + refresh). Sends a
     * form-encoded body with
     * {@code Authorization: Basic} + a {@code CLIENT_ID} header. Never triggers the
     * 401-retry loop.
     */
    public <T> T postRaw(String path, Map<String, String> form, TypeReference<T> type) {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (body.length() > 0)
                body.append('&');
            body.append(e.getKey()).append('=').append(urlEnc(e.getValue()));
        }
        String basic = Base64.getEncoder().encodeToString(
                (config.getClientId() + ":" + config.getClientSecret())
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Request req = new Request.Builder()
                .url(buildUrl(path, null))
                .header("Authorization", "Basic " + basic)
                .header("CLIENT_ID", config.getClientId())
                .header("Accept", "application/json")
                .post(RequestBody.create(body.toString(), FORM))
                .build();
        return execute(req, type, /* allowRetry */ false);
    }

    private Request buildRequest(String method, String path, String query, RequestBody body) {
        Request.Builder b = new Request.Builder()
                .url(buildUrl(path, query))
                .header("Accept", "application/json");
        if (credentials != null && credentials.getAccessToken() != null) {
            b.header("Authorization", "Bearer " + credentials.getAccessToken());
        }
        switch (method) {
            case "GET":
                b.get();
                break;
            case "DELETE":
                b.delete(body);
                break;
            case "POST":
                b.post(body != null ? body : RequestBody.create(new byte[0], null));
                break;
            case "PUT":
                b.put(body != null ? body : RequestBody.create(new byte[0], null));
                break;
            default:
                throw new IllegalArgumentException("Unsupported method: " + method);
        }
        return b.build();
    }

    private HttpUrl buildUrl(String path, String query) {
        String full = config.getBaseUrl() + ensureLeadingSlash(path);
        if (query != null && !query.isEmpty()) {
            full = full + "?" + query;
        }
        HttpUrl parsed = HttpUrl.parse(full);
        if (parsed == null) {
            throw new MobiscrollConnectException("Invalid URL: " + full);
        }
        return parsed;
    }

    private static String ensureLeadingSlash(String s) {
        if (s == null || s.isEmpty())
            return "/";
        return s.startsWith("/") ? s : "/" + s;
    }

    private RequestBody jsonBody(Object body) {
        if (body == null) {
            return RequestBody.create(new byte[0], JSON);
        }
        try {
            byte[] bytes = json.writeValueAsBytes(body);
            return RequestBody.create(bytes, JSON);
        } catch (IOException e) {
            throw new MobiscrollConnectException("Failed to serialize request body", e);
        }
    }

    private <T> T execute(Request request, TypeReference<T> type, boolean allowRetry) {
        Response response;
        try {
            response = http.newCall(request).execute();
        } catch (IOException e) {
            throw new NetworkException("Network error: " + e.getMessage(), e);
        }

        try {
            int code = response.code();
            if (code == 401 && allowRetry && credentials != null && credentials.getRefreshToken() != null) {
                response.close();
                TokenResponse refreshed = refreshAccessToken();
                Request retried = request.newBuilder()
                        .header("Authorization", "Bearer " + refreshed.getAccessToken())
                        .build();
                try {
                    response = http.newCall(retried).execute();
                } catch (IOException e) {
                    throw new NetworkException("Network error: " + e.getMessage(), e);
                }
            }
            if (!response.isSuccessful()) {
                throw mapError(response);
            }
            if (type == null) {
                return null;
            }
            ResponseBody body = response.body();
            if (body == null || body.contentLength() == 0) {
                return null;
            }
            String text;
            try {
                text = body.string();
            } catch (IOException e) {
                throw new NetworkException("Failed to read response body: " + e.getMessage(), e);
            }
            if (text.isEmpty()) {
                return null;
            }
            try {
                return json.readValue(text, type);
            } catch (IOException e) {
                throw new MobiscrollConnectException("Failed to parse response: " + e.getMessage(), e);
            }
        } finally {
            response.close();
        }
    }

    private TokenResponse refreshAccessToken() {
        CompletableFuture<TokenResponse> future = new CompletableFuture<>();
        boolean winner = inflight.compareAndSet(null, future);
        if (!winner) {
            CompletableFuture<TokenResponse> existing = inflight.get();
            if (existing != null) {
                try {
                    return existing.join();
                } catch (CompletionException ce) {
                    Throwable cause = ce.getCause() != null ? ce.getCause() : ce;
                    if (cause instanceof RuntimeException)
                        throw (RuntimeException) cause;
                    throw new MobiscrollConnectException("Token refresh failed", cause);
                }
            }
            // The winner cleared the slot already; retry once.
            return refreshAccessToken();
        }
        try {
            TokenResponse incoming = postRaw("/oauth/token",
                    formMap("grant_type", "refresh_token",
                            "refresh_token", credentials.getRefreshToken()),
                    new TypeReference<TokenResponse>() {
                    });
            TokenResponse merged = credentials.mergedWith(incoming);
            this.credentials = merged;
            Consumer<TokenResponse> cb = onTokensRefreshedOverride != null
                    ? onTokensRefreshedOverride
                    : config.getOnTokensRefreshed();
            if (cb != null) {
                try {
                    cb.accept(merged);
                } catch (RuntimeException ignored) {
                    /* never break refresh */ }
            }
            future.complete(merged);
            return merged;
        } catch (RuntimeException e) {
            future.completeExceptionally(e);
            throw e;
        } finally {
            inflight.set(null);
        }
    }

    private static Map<String, String> formMap(String... kv) {
        if (kv.length % 2 != 0)
            throw new IllegalArgumentException("kv must be even length");
        java.util.LinkedHashMap<String, String> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    private MobiscrollConnectException mapError(Response response) {
        int code = response.code();
        String bodyText = "";
        ResponseBody rb = response.body();
        if (rb != null) {
            try {
                bodyText = rb.string();
            } catch (IOException ignored) {
                /* swallow */ }
        }
        JsonNode bodyJson = null;
        try {
            if (!bodyText.isEmpty()) {
                bodyJson = json.readTree(bodyText);
            }
        } catch (IOException ignored) {
            // non-JSON error body — fall through
        }
        String message = extractMessage(bodyJson, response.message(), code);

        switch (code) {
            case 401:
            case 403:
                return new AuthenticationException(message);
            case 404:
                return new NotFoundException(message);
            case 400:
            case 422: {
                JsonNode details = bodyJson != null ? bodyJson.get("details") : null;
                return new ValidationException(message, details);
            }
            case 429: {
                Integer retryAfter = null;
                String header = response.header("Retry-After");
                if (header != null) {
                    try {
                        retryAfter = Integer.parseInt(header.trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
                return new RateLimitException(message, retryAfter);
            }
            default:
                if (code >= 500 && code < 600) {
                    return new ServerException(message, code);
                }
                return new MobiscrollConnectException(message);
        }
    }

    private static String extractMessage(JsonNode body, String fallback, int status) {
        if (body != null) {
            JsonNode m = body.get("message");
            if (m != null && m.isTextual())
                return m.asText();
            JsonNode err = body.get("error");
            if (err != null && err.isTextual())
                return err.asText();
        }
        if (fallback != null && !fallback.isEmpty())
            return fallback;
        return "HTTP " + status;
    }

    private static String urlEnc(String s) {
        try {
            return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Returns true iff a credential with an access token is currently set. */
    public boolean hasCredentials() {
        return credentials != null && credentials.getAccessToken() != null;
    }
}
