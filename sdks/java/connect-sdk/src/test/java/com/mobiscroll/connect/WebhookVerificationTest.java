package com.mobiscroll.connect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import com.mobiscroll.connect.exceptions.WebhookVerificationException;
import com.mobiscroll.connect.exceptions.WebhookVerificationException.Reason;
import com.mobiscroll.connect.internal.WebhookKeyStore;
import com.mobiscroll.connect.models.WebhookDelivery;
import com.mobiscroll.connect.models.WebhookEvent;
import com.mobiscroll.connect.support.ClientFactory;
import com.mobiscroll.connect.support.WebhookVectors;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebhookVerificationTest {

    private static final String KEYS_PATH = "/.well-known/webhook-keys";

    private final WebhookVectors.Case valid = WebhookVectors.byName("valid single signature");
    private final AtomicLong clock = new AtomicLong();
    private MockWebServer server;

    @BeforeEach void setUp() throws Exception {
        WebhookKeyStore.reset();
        clock.set(valid.now * 1000);
        WebhookKeyStore.useClock(clock::get);
        server = new MockWebServer();
        server.start();
    }

    @AfterEach void tearDown() throws Exception {
        WebhookKeyStore.reset();
        server.shutdown();
    }

    private static MockResponse keysResponse(String key, String cacheControl) {
        return new MockResponse()
                .setHeader("Cache-Control", cacheControl)
                .setBody("{\"keys\":[{\"id\":\"k1\",\"alg\":\"ed25519\",\"key\":\"" + key
                        + "\",\"status\":\"active\"}]}");
    }

    private static MockResponse keysResponse(String key) {
        return keysResponse(key, "public, max-age=3600");
    }

    private void serve(Supplier<MockResponse> keys) {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return KEYS_PATH.equals(request.getPath()) ? keys.get() : new MockResponse().setResponseCode(404);
            }
        });
    }

    private MobiscrollConnectClient client(String webhookPublicKey) {
        return new MobiscrollConnectClient(MobiscrollConnectConfig.builder()
                .clientId("test-client")
                .clientSecret("test-secret")
                .redirectUri("http://localhost/callback")
                .baseUrl(server.url("/api").toString())
                .webhookPublicKey(webhookPublicKey)
                .build());
    }

    private String keysUrl() {
        return server.url(KEYS_PATH).toString();
    }

    private static Reason reasonOf(Throwable t) {
        return ((WebhookVerificationException) t).getReason();
    }

    @Test void fetchesKeysLazilyFromTheOriginWithoutAuthAndReturnsTheDelivery() throws Exception {
        serve(() -> keysResponse(WebhookVectors.key("active")));
        MobiscrollConnectClient client = ClientFactory.withMockAndCredentials(server);
        assertThat(server.getRequestCount()).isZero();

        WebhookDelivery delivery = client.webhooks().verifyWebhook(valid.body, valid.headers);

        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).isEqualTo(KEYS_PATH);
        assertThat(request.getHeader("Authorization")).isNull();
        assertThat(delivery.getUserId()).isEqualTo("user_42");
        assertThat(delivery.getProvider()).isEqualTo(Provider.GOOGLE);
        assertThat(delivery.getEvents().get(0).getTitle()).isEqualTo("Café meeting ☕ — Zoë");
    }

    @Test void sharesOneCacheAcrossClientsAndHonoursMaxAge() {
        serve(() -> keysResponse(WebhookVectors.key("active"), "max-age=120"));

        client(null).webhooks().verifyWebhook(valid.body, valid.headers);
        client(null).webhooks().verifyWebhook(valid.body, valid.headers);
        assertThat(server.getRequestCount()).isEqualTo(1);

        clock.addAndGet(121_000);
        client(null).webhooks().verifyWebhook(valid.body, valid.headers);
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test void refetchesOnceAfterARotationAndAcceptsTheDelivery() {
        server.enqueue(keysResponse(WebhookVectors.key("unrelated")));
        server.enqueue(keysResponse(WebhookVectors.key("active")));
        clock.addAndGet(-61_000);
        assertThat(WebhookKeyStore.forUrl(keysUrl()).getKeys()).containsExactly(WebhookVectors.key("unrelated"));
        clock.addAndGet(61_000);

        WebhookDelivery delivery = client(null).webhooks().verifyWebhook(valid.body, valid.headers);

        assertThat(delivery.getCalendarId()).isEqualTo("primary");
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test void doesNotRefetchMoreThanOnceAMinuteOnForgedDeliveries() {
        serve(() -> keysResponse(WebhookVectors.key("active")));
        MobiscrollConnectClient client = client(null);
        WebhookVectors.Case forged = WebhookVectors.byName("tampered body");

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> client.webhooks().verifyWebhook(forged.body, forged.headers))
                    .isInstanceOf(WebhookVerificationException.class)
                    .satisfies(t -> assertThat(reasonOf(t)).isEqualTo(Reason.NO_MATCHING_SIGNATURE));
        }
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test void throwsOtherFailuresWithoutRefetching() {
        serve(() -> keysResponse(WebhookVectors.key("active")));
        clock.addAndGet(301_000);

        assertThatThrownBy(() -> client(null).webhooks().verifyWebhook(valid.body, valid.headers))
                .satisfies(t -> assertThat(reasonOf(t)).isEqualTo(Reason.TIMESTAMP_OUT_OF_TOLERANCE));
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test void fallsBackToThePinnedKeyWhenTheEndpointCannotBeReached() {
        serve(() -> new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

        WebhookDelivery delivery = client(WebhookVectors.key("active")).webhooks()
                .verifyWebhook(valid.body, valid.headers);

        assertThat(delivery.getProvider()).isEqualTo(Provider.GOOGLE);
    }

    @Test void ignoresThePinnedKeyWhileFetchedKeysAreAvailable() {
        serve(() -> keysResponse(WebhookVectors.key("unrelated")));

        assertThatThrownBy(() -> client(WebhookVectors.key("active")).webhooks()
                .verifyWebhook(valid.body, valid.headers))
                .satisfies(t -> assertThat(reasonOf(t)).isEqualTo(Reason.NO_MATCHING_SIGNATURE));
    }

    @Test void keepsTheLastGoodKeysWhenARefreshFails() {
        server.enqueue(keysResponse(WebhookVectors.key("active")));
        server.enqueue(new MockResponse().setResponseCode(500));
        MobiscrollConnectClient client = client(null);
        client.webhooks().verifyWebhook(valid.body, valid.headers);

        clock.addAndGet(3_601_000);
        Map<String, String> later = new LinkedHashMap<>(valid.headers);
        later.put("webhook-timestamp", String.valueOf(valid.now + 3601));

        assertThatThrownBy(() -> client.webhooks().verifyWebhook(valid.body, later))
                .satisfies(t -> assertThat(reasonOf(t)).isEqualTo(Reason.NO_MATCHING_SIGNATURE));
        assertThat(server.getRequestCount()).isEqualTo(2);
        assertThat(WebhookKeyStore.forUrl(keysUrl()).getKeys()).containsExactly(WebhookVectors.key("active"));
    }

    @Test void failsWithNoPublicKeysWhenNothingIsAvailable() {
        serve(() -> new MockResponse().setResponseCode(503));

        assertThatThrownBy(() -> client(null).webhooks().verifyWebhook(valid.body, valid.headers))
                .satisfies(t -> assertThat(reasonOf(t)).isEqualTo(Reason.NO_PUBLIC_KEYS));
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test void concurrentCallsShareOneFetch() throws Exception {
        serve(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return keysResponse(WebhookVectors.key("active"));
        });
        MobiscrollConnectClient client = client(null);
        int parallel = 8;
        ExecutorService exec = Executors.newFixedThreadPool(parallel);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<WebhookDelivery>> results = new ArrayList<>();
        try {
            for (int i = 0; i < parallel; i++) {
                results.add(exec.submit(() -> {
                    go.await();
                    return client.webhooks().verifyWebhook(valid.body, valid.headers);
                }));
            }
            go.countDown();
            for (Future<WebhookDelivery> result : results) {
                assertThat(result.get(10, TimeUnit.SECONDS).getUserId()).isEqualTo("user_42");
            }
        } finally {
            exec.shutdownNow();
        }
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test void looksUpHeadersCaseInsensitivelyInMultiValueMaps() {
        serve(() -> keysResponse(WebhookVectors.key("active")));
        Map<String, List<String>> headers = new LinkedHashMap<>();
        valid.headers.forEach((name, value) -> headers.put(name.toUpperCase(), List.of(value)));

        WebhookDelivery delivery = client(null).webhooks()
                .verifyWebhook(valid.body.getBytes(StandardCharsets.UTF_8), headers);

        assertThat(delivery.getUserId()).isEqualTo("user_42");
    }

    @Test void parsesTheFullDeliveryShapeAndRejectsNonJson() throws Exception {
        serve(() -> new MockResponse().setResponseCode(500));
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] spki = pair.getPublic().getEncoded();
        String pinned = "whpk_" + Base64.getEncoder().encodeToString(Arrays.copyOfRange(spki, spki.length - 32, spki.length));
        MobiscrollConnectClient client = client(pinned);

        String body = "{\"provider\":\"microsoft\",\"userId\":\"u1\",\"calendarId\":\"c1\",\"changeType\":\"mixed\","
                + "\"timestamp\":\"2026-09-21T10:00:00.000Z\",\"unknown\":1,"
                + "\"events\":[{\"id\":\"e1\",\"provider\":\"microsoft\",\"title\":\"T\","
                + "\"start\":\"2026-09-21T10:00:00Z\",\"changeType\":\"deleted\",\"extra\":true}],"
                + "\"metadata\":{\"channelId\":\"ch1\",\"eventCount\":1,\"isInitialSync\":true}}";
        WebhookDelivery delivery = client.webhooks().verifyWebhook(body, sign(pair, body));

        assertThat(delivery.getProvider()).isEqualTo(Provider.MICROSOFT);
        assertThat(delivery.getChangeType()).isEqualTo("mixed");
        assertThat(delivery.getTimestamp()).isEqualTo("2026-09-21T10:00:00.000Z");
        assertThat(delivery.getEvents().get(0).getChangeType()).isEqualTo("deleted");
        assertThat(delivery.getEvents().get(0).getStart()).isNotNull();
        assertThat(delivery.getEvents().get(0).getAdditional()).containsEntry("extra", true);
        assertThat(delivery.getMetadata().getChannelId()).isEqualTo("ch1");
        assertThat(delivery.getMetadata().getEventCount()).isEqualTo(1);
        assertThat(delivery.getMetadata().isInitialSync()).isTrue();

        assertThatThrownBy(() -> client.webhooks().verifyWebhook("not json", sign(pair, "not json")))
                .satisfies(t -> assertThat(reasonOf(t)).isEqualTo(Reason.INVALID_PAYLOAD));
    }

    @Test void parsesADeliveryWithAConferenceObject() {
        serve(() -> keysResponse(WebhookVectors.key("active")));
        WebhookVectors.Case vector = WebhookVectors.byName("delivery: Google events with and without a conference");

        WebhookDelivery delivery = client(null).webhooks().verifyWebhook(vector.body, vector.headers);

        WebhookEvent meeting = delivery.getEvents().get(0);
        assertThat(meeting.getConference()).isEqualTo("https://meet.google.com/abc-defg-hij");
        assertThat(meeting.getConferenceData()).containsEntry("provider", "hangoutsMeet");
        assertThat(meeting.getConferenceData()).containsEntry("conferenceId", "abc-defg-hij");
        assertThat(meeting.getStart()).isEqualTo(OffsetDateTime.of(2026, 9, 22, 9, 0, 0, 0, ZoneOffset.UTC));
        assertThat(meeting.getLastModified()).isEqualTo("2026-09-21T09:58:12.000Z");
        assertThat(meeting.getChangeType()).isEqualTo("updated");
        assertThat(meeting.getAttendees().get(0).getOrganizer()).isTrue();
        assertThat(delivery.getEvents().get(1).getConference()).isNull();
        assertThat(delivery.getEvents().get(1).getConferenceData()).isNull();
        assertThat(delivery.getEvents().get(1).getAllDay()).isTrue();
        assertThat(delivery.getMetadata().getChannelId()).isEqualTo("chan_google_1");
    }

    @Test void parsesADeletedPlaceholderWithoutCalendarId() {
        serve(() -> keysResponse(WebhookVectors.key("active")));
        WebhookVectors.Case vector = WebhookVectors.byName("delivery: Microsoft Teams event and a deleted placeholder");

        WebhookDelivery delivery = client(null).webhooks().verifyWebhook(vector.body, vector.headers);

        assertThat(delivery.getEvents().get(0).getConferenceData()).containsEntry("provider", "teams");
        WebhookEvent deleted = delivery.getEvents().get(1);
        assertThat(deleted.getChangeType()).isEqualTo("deleted");
        assertThat(deleted.getCalendarId()).isNull();
        assertThat(deleted.getOriginal()).isNull();
    }

    @Test void keepsTheApi161ConferenceDataAndCalendarIdOnDeletedEvents() {
        serve(() -> keysResponse(WebhookVectors.key("active")));

        WebhookDelivery google = verifyVector("delivery: 1.6.1 Google Meet event with conferenceData and a cancelled event with calendarId");
        WebhookEvent meet = google.getEvents().get(0);
        assertThat(meet.getConference()).isEqualTo("https://meet.google.com/abc-defg-hij");
        assertThat(meet.getConferenceData()).containsEntry("provider", "hangoutsMeet");
        assertThat(meet.getConferenceData()).containsEntry("url", "https://meet.google.com/abc-defg-hij");
        assertThat(meet.getConferenceData()).containsEntry("conferenceId", "abc-defg-hij");
        assertThat(meet.getConferenceData()).extractingByKey("original").isInstanceOf(java.util.Map.class);
        assertThat(((java.util.Map<?, ?>) meet.getConferenceData().get("original")).get("conferenceId")).isEqualTo("abc-defg-hij");
        assertThat(google.getEvents().get(1).getCalendarId()).isEqualTo("work@company.com");

        WebhookDelivery microsoft = verifyVector("delivery: 1.6.1 Microsoft Teams event with conferenceData and a deleted placeholder with calendarId");
        assertThat(microsoft.getEvents().get(0).getConferenceData()).containsEntry("provider", "teams");
        assertThat(((java.util.Map<?, ?>) microsoft.getEvents().get(0).getConferenceData().get("original")).get("conferenceId")).isEqualTo("123456789");
        assertThat(microsoft.getEvents().get(1).getCalendarId()).isEqualTo("AAMkAGI2Y2FsAAA=");

        WebhookDelivery apple = verifyVector("delivery: 1.6.1 Apple event with a URL as conference and a deleted placeholder with calendarId");
        assertThat(apple.getEvents().get(0).getConference()).isEqualTo("https://example.com/room/42");
        assertThat(apple.getEvents().get(0).getConferenceData()).containsOnly(
                java.util.Map.entry("provider", "caldav"), java.util.Map.entry("url", "https://example.com/room/42"));
        assertThat(apple.getEvents().get(1).getCalendarId()).isEqualTo("https://caldav.icloud.com/1234567/calendars/work/");
    }

    private WebhookDelivery verifyVector(String name) {
        WebhookVectors.Case vector = WebhookVectors.byName(name);
        return client(null).webhooks().verifyWebhook(vector.body, vector.headers);
    }

    /** Signs a delivery holding one event with a fresh key, pinned in the client, and verifies it. */
    private WebhookEvent verifyEvent(String eventJson, String deliveryProvider) throws Exception {
        serve(() -> new MockResponse().setResponseCode(500));
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] spki = pair.getPublic().getEncoded();
        String pinned = "whpk_" + Base64.getEncoder().encodeToString(Arrays.copyOfRange(spki, spki.length - 32, spki.length));
        String body = "{\"provider\":\"" + deliveryProvider + "\",\"userId\":\"u1\",\"calendarId\":\"c1\","
                + "\"events\":[" + eventJson + "],"
                + "\"metadata\":{\"channelId\":\"ch1\",\"eventCount\":1,\"isInitialSync\":false}}";
        WebhookDelivery delivery = client(pinned).webhooks().verifyWebhook(body, sign(pair, body));
        assertThat(delivery.getEvents()).hasSize(1);
        return delivery.getEvents().get(0);
    }

    private static String eventJson(String provider, String extra) {
        return "{\"id\":\"e1\",\"provider\":\"" + provider + "\",\"calendarId\":\"c1\",\"title\":\"T\","
                + "\"start\":\"2026-09-21T10:00:00.000Z\",\"end\":\"2026-09-21T11:00:00.000Z\","
                + "\"changeType\":\"updated\"" + (extra == null ? "" : "," + extra) + "}";
    }

    @Test void convertsAConferenceObjectToTheUrlAndRestConferenceData() throws Exception {
        WebhookEvent event = verifyEvent(eventJson("google",
                "\"conference\":{\"url\":\"https://meet.google.com/abc\",\"provider\":\"hangoutsMeet\","
                        + "\"meetingProvider\":\"hangoutsMeet\",\"autoGenerate\":false,"
                        + "\"data\":{\"conferenceId\":\"abc\",\"entryPoints\":[{\"uri\":\"https://meet.google.com/abc\"}]}}"),
                "google");

        assertThat(event.getConference()).isEqualTo("https://meet.google.com/abc");
        assertThat(event.getConferenceData()).containsExactly(
                Map.entry("provider", "hangoutsMeet"),
                Map.entry("autoGenerate", false),
                Map.entry("conferenceId", "abc"),
                Map.entry("entryPoints", List.of(Map.of("uri", "https://meet.google.com/abc"))));
        assertThat(event.getChangeType()).isEqualTo("updated");
    }

    @Test void infersTheConferenceProviderFromTheEventProvider() throws Exception {
        String[][] cases = {{"google", "google-meet"}, {"microsoft", "teams"}, {"apple", "caldav"}, {"caldav", "caldav"}};
        for (String[] c : cases) {
            WebhookEvent event = verifyEvent(
                    eventJson(c[0], "\"conference\":{\"url\":\"https://x.test/j\",\"provider\":\"\"}"), "google");

            assertThat(event.getConference()).isEqualTo("https://x.test/j");
            assertThat(event.getConferenceData()).containsExactly(Map.entry("provider", c[1]));
        }
    }

    @Test void infersTheConferenceProviderFromTheDeliveryWhenTheEventHasNone() throws Exception {
        WebhookEvent event = verifyEvent(
                "{\"id\":\"e1\",\"title\":\"T\",\"start\":\"2026-09-21T10:00:00.000Z\","
                        + "\"conference\":{\"url\":\"https://x.test/j\"}}",
                "microsoft");

        assertThat(event.getConferenceData()).containsExactly(Map.entry("provider", "teams"));
    }

    @Test void aConferenceObjectWithoutAUrlHasNullConference() throws Exception {
        WebhookEvent autoOnly = verifyEvent(eventJson("google", "\"conference\":{\"autoGenerate\":true}"), "google");
        assertThat(autoOnly.getConference()).isNull();
        assertThat(autoOnly.getConferenceData()).containsExactly(
                Map.entry("provider", "google-meet"), Map.entry("autoGenerate", true));

        WebhookEvent emptyUrl = verifyEvent(
                eventJson("caldav", "\"conference\":{\"url\":\"\",\"data\":{\"k\":1}}"), "caldav");
        assertThat(emptyUrl.getConference()).isNull();
        assertThat(emptyUrl.getConferenceData()).containsExactly(
                Map.entry("provider", "caldav"), Map.entry("k", 1));

        WebhookEvent nonString = verifyEvent(
                eventJson("google", "\"conference\":{\"url\":5,\"autoGenerate\":\"yes\"}"), "google");
        assertThat(nonString.getConference()).isNull();
        assertThat(nonString.getConferenceData()).containsExactly(Map.entry("provider", "google-meet"));
    }

    @Test void leavesAStringConferenceUnchanged() throws Exception {
        WebhookEvent event = verifyEvent(eventJson("google", "\"conference\":\"https://meet.test/s\""), "google");

        assertThat(event.getConference()).isEqualTo("https://meet.test/s");
        assertThat(event.getConferenceData()).isNull();
    }

    @Test void acceptsANullOrAbsentConference() throws Exception {
        for (String extra : new String[] {null, "\"conference\":null"}) {
            WebhookEvent event = verifyEvent(eventJson("google", extra), "google");

            assertThat(event.getConference()).isNull();
            assertThat(event.getConferenceData()).isNull();
        }
    }

    @Test void rejectsAConferenceThatIsNotAStringOrObject() {
        for (String conference : new String[] {"5", "true", "[1,2]", "[]"}) {
            assertThatThrownBy(() -> verifyEvent(eventJson("google", "\"conference\":" + conference), "google"))
                    .as(conference)
                    .satisfies(t -> assertThat(reasonOf(t)).isEqualTo(Reason.INVALID_PAYLOAD));
        }
    }

    @Test void leavesTheEventsOwnConferenceDataUntouched() throws Exception {
        WebhookEvent event = verifyEvent(eventJson("google",
                "\"conference\":{\"url\":\"https://x.test/j\",\"provider\":\"teams\",\"data\":{\"a\":1}},"
                        + "\"conferenceData\":{\"own\":true}"), "google");

        assertThat(event.getConference()).isEqualTo("https://x.test/j");
        assertThat(event.getConferenceData()).containsExactly(Map.entry("own", true));
    }

    @Test void acceptsADeletedPlaceholderWithoutCalendarIdOrOriginal() throws Exception {
        WebhookEvent event = verifyEvent("{\"provider\":\"microsoft\",\"id\":\"gone_1\",\"changeType\":\"deleted\"}", "microsoft");

        assertThat(event.getChangeType()).isEqualTo("deleted");
        assertThat(event.getCalendarId()).isNull();
        assertThat(event.getOriginal()).isNull();
        assertThat(event.getConference()).isNull();
    }

    @Test void aWebhookEventIsACalendarEvent() {
        assertThat(com.mobiscroll.connect.models.CalendarEvent.class).isAssignableFrom(WebhookEvent.class);
    }

    private Map<String, String> sign(KeyPair pair, String body) throws Exception {
        String id = "msg_test";
        String timestamp = String.valueOf(clock.get() / 1000);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update((id + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("webhook-id", id);
        headers.put("webhook-timestamp", timestamp);
        headers.put("webhook-signature", "v1a," + Base64.getEncoder().encodeToString(signer.sign()));
        return headers;
    }
}
