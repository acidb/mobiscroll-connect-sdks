package com.mobiscroll.connect.internal;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Process-wide cache of the webhook public keys published at one keys URL, shared by every client
 * instance. Follows the endpoint's {@code Cache-Control}, fetches at most once a minute, and keeps the
 * last good keys when a fetch fails. Internal; not part of the public API.
 */
public final class WebhookKeyStore {

    /** Path of the keys endpoint, relative to the origin of the API base URL. */
    public static final String KEYS_PATH = "/.well-known/webhook-keys";

    private static final String PUBLIC_KEY_PREFIX = "whpk_";
    private static final long DEFAULT_MAX_AGE_MS = Duration.ofHours(1).toMillis();
    private static final long MAX_MAX_AGE_MS = Duration.ofHours(24).toMillis();
    private static final long MIN_REFETCH_INTERVAL_MS = Duration.ofMinutes(1).toMillis();
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(10);
    private static final Pattern MAX_AGE = Pattern.compile("max-age=(\\d+)", Pattern.CASE_INSENSITIVE);

    private static final ConcurrentMap<String, WebhookKeyStore> STORES = new ConcurrentHashMap<>();
    private static final OkHttpClient HTTP = new OkHttpClient.Builder().callTimeout(FETCH_TIMEOUT).build();
    private static final LongSupplier SYSTEM_CLOCK = System::currentTimeMillis;
    private static volatile LongSupplier clock = SYSTEM_CLOCK;

    private final String url;
    private volatile List<String> keys = List.of();
    private volatile long fetchedAt;
    private volatile long lastAttemptAt;
    private volatile long maxAgeMs = DEFAULT_MAX_AGE_MS;
    /** Guarded by {@code this}. */
    private CompletableFuture<Void> inFlight;

    private WebhookKeyStore(String url) {
        this.url = url;
    }

    /** The store for a keys URL, created on first use. */
    public static WebhookKeyStore forUrl(String url) {
        return STORES.computeIfAbsent(url, WebhookKeyStore::new);
    }

    /** Clears every cached key set and restores the system clock; for tests. */
    public static void reset() {
        STORES.clear();
        clock = SYSTEM_CLOCK;
    }

    /** Replaces the clock (Unix milliseconds) used for caching and timestamp checks; for tests. */
    public static void useClock(LongSupplier millis) {
        clock = millis;
    }

    /** Current time in Unix milliseconds, as the store sees it. */
    public static long currentTimeMillis() {
        return clock.getAsLong();
    }

    /**
     * The cached keys, fetched first if they are older than the endpoint's {@code max-age} and the
     * last attempt was over a minute ago. Waits for a fetch already in flight. Never throws; returns
     * an empty list when no keys were ever fetched.
     */
    public List<String> getKeys() {
        awaitFetch(true);
        return keys;
    }

    /** Whether a fetch may be attempted now, i.e. the last attempt was at least a minute ago. */
    public boolean canRefetch() {
        return currentTimeMillis() - lastAttemptAt >= MIN_REFETCH_INTERVAL_MS;
    }

    /** Fetches the keys now unless the last attempt was within a minute; waits for a fetch already in flight. */
    public void refresh() {
        awaitFetch(false);
    }

    private void awaitFetch(boolean onlyIfStale) {
        CompletableFuture<Void> pending;
        boolean owner = false;
        synchronized (this) {
            pending = inFlight;
            if (pending == null) {
                long now = currentTimeMillis();
                boolean stale = now - fetchedAt > maxAgeMs;
                if ((onlyIfStale && !stale) || now - lastAttemptAt < MIN_REFETCH_INTERVAL_MS) {
                    return;
                }
                lastAttemptAt = now;
                pending = new CompletableFuture<>();
                inFlight = pending;
                owner = true;
            }
        }
        if (!owner) {
            pending.join();
            return;
        }
        try {
            fetchKeys();
        } finally {
            synchronized (this) {
                inFlight = null;
            }
            pending.complete(null);
        }
    }

    private void fetchKeys() {
        Request request = new Request.Builder().url(url).header("Accept", "application/json").get().build();
        try (Response response = HTTP.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                return;
            }
            JsonNode entries = JsonMapperHolder.get().readTree(body.string()).path("keys");
            if (!entries.isArray()) {
                return;
            }
            List<String> fetched = new ArrayList<>();
            for (JsonNode entry : entries) {
                JsonNode key = entry.path("key");
                JsonNode alg = entry.path("alg");
                boolean ed25519 = alg.isMissingNode() || alg.isNull()
                        || (alg.isTextual() && (alg.asText().isEmpty() || alg.asText().equalsIgnoreCase("ed25519")));
                if (key.isTextual() && key.asText().startsWith(PUBLIC_KEY_PREFIX) && ed25519) {
                    fetched.add(key.asText());
                }
            }
            if (!fetched.isEmpty()) {
                keys = List.copyOf(fetched);
                fetchedAt = currentTimeMillis();
                maxAgeMs = parseMaxAge(response.header("Cache-Control"));
            }
        } catch (IOException | RuntimeException e) {
            // Keep the last good keys; the caller falls back to a pinned key when there are none.
        }
    }

    private static long parseMaxAge(String cacheControl) {
        Matcher match = cacheControl == null ? null : MAX_AGE.matcher(cacheControl);
        if (match == null || !match.find()) {
            return DEFAULT_MAX_AGE_MS;
        }
        try {
            return Math.min(Math.multiplyExact(Long.parseLong(match.group(1)), 1000L), MAX_MAX_AGE_MS);
        } catch (ArithmeticException | NumberFormatException tooLarge) {
            return MAX_MAX_AGE_MS;
        }
    }
}
