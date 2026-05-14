package com.mobiscroll.connect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.mobiscroll.connect.support.ClientFactory;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenRefreshConcurrencyTest {

    private MockWebServer server;

    @BeforeEach void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach void tearDown() throws Exception { server.shutdown(); }

    @Test void concurrent401sShareASingleRefresh() throws Exception {
        int parallel = 10;
        AtomicInteger refreshCount = new AtomicInteger();
        AtomicInteger firstCalendarsCount = new AtomicInteger();

        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath() != null ? request.getPath() : "";
                if (path.startsWith("/api/oauth/token")) {
                    refreshCount.incrementAndGet();
                    // Slow down the refresh so concurrent callers pile up on the inflight future.
                    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                    return new MockResponse().setBody(
                            "{\"access_token\":\"access-2\",\"token_type\":\"Bearer\",\"expires_in\":3600,\"refresh_token\":\"refresh-2\"}");
                }
                if (path.startsWith("/api/calendars")) {
                    String auth = request.getHeader("Authorization");
                    if ("Bearer access-1".equals(auth) && firstCalendarsCount.getAndIncrement() < parallel) {
                        return new MockResponse().setResponseCode(401);
                    }
                    return new MockResponse().setBody("[]");
                }
                return new MockResponse().setResponseCode(404);
            }
        });

        MobiscrollConnectClient client = ClientFactory.withMockAndCredentials(server);

        ExecutorService exec = Executors.newFixedThreadPool(parallel);
        CountDownLatch ready = new CountDownLatch(parallel);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(parallel);
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < parallel; i++) {
            exec.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    client.calendars().list();
                } catch (Throwable t) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        go.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        exec.shutdownNow();

        assertThat(failures).hasValue(0);
        assertThat(refreshCount).as("token refresh should be deduplicated").hasValue(1);
        assertThat(client.getCredentials().getAccessToken()).isEqualTo("access-2");
    }
}
