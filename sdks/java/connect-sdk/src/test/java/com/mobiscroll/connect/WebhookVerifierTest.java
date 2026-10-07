package com.mobiscroll.connect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.mobiscroll.connect.exceptions.MobiscrollConnectException;
import com.mobiscroll.connect.exceptions.WebhookVerificationException;
import com.mobiscroll.connect.exceptions.WebhookVerificationException.Reason;
import com.mobiscroll.connect.models.VerifyWebhookSignatureOptions;
import com.mobiscroll.connect.support.WebhookVectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class WebhookVerifierTest {

    static Stream<WebhookVectors.Case> vectors() {
        return WebhookVectors.cases().stream();
    }

    private static VerifyWebhookSignatureOptions at(long now) {
        return VerifyWebhookSignatureOptions.builder().now(now).build();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vectors")
    void matchesTheServerSigner(WebhookVectors.Case vector) {
        if (vector.valid) {
            assertThatCode(() -> WebhookVerifier.verifyWebhookSignature(
                    vector.body, vector.headers, vector.publicKeys, at(vector.now)))
                    .doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> WebhookVerifier.verifyWebhookSignature(
                    vector.body, vector.headers, vector.publicKeys, at(vector.now)))
                    .isInstanceOf(WebhookVerificationException.class);
        }
    }

    @Test void acceptsBytesAndMultiValueHeadersInAnyCasing() {
        WebhookVectors.Case vector = WebhookVectors.byName("valid single signature");
        Map<String, List<String>> headers = new LinkedHashMap<>();
        vector.headers.forEach((name, value) -> headers.put(name.toUpperCase(), List.of(value, "ignored")));

        assertThatCode(() -> WebhookVerifier.verifyWebhookSignature(
                vector.body.getBytes(StandardCharsets.UTF_8), headers, vector.publicKeys, at(vector.now)))
                .doesNotThrowAnyException();
    }

    @Test void reportsWhyVerificationFailed() {
        WebhookVectors.Case stale = WebhookVectors.byName("timestamp 301 s old, stale");

        assertThatThrownBy(() -> WebhookVerifier.verifyWebhookSignature(
                stale.body, stale.headers, stale.publicKeys, at(stale.now)))
                .isInstanceOf(MobiscrollConnectException.class)
                .isInstanceOfSatisfying(WebhookVerificationException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(Reason.TIMESTAMP_OUT_OF_TOLERANCE);
                    assertThat(e.getReason().wireValue()).isEqualTo("timestamp_out_of_tolerance");
                });
    }

    @Test void reasonsForEachFailure() {
        assertReason("missing webhook-id", Reason.MISSING_HEADERS);
        assertReason("non-numeric timestamp", Reason.INVALID_TIMESTAMP);
        assertReason("no public keys", Reason.NO_PUBLIC_KEYS);
        assertReason("tampered body", Reason.NO_MATCHING_SIGNATURE);
        assertReason("malformed base64 signature", Reason.NO_MATCHING_SIGNATURE);
    }

    @Test void honoursACustomTolerance() {
        WebhookVectors.Case stale = WebhookVectors.byName("timestamp 301 s old, stale");
        assertThatCode(() -> WebhookVerifier.verifyWebhookSignature(stale.body, stale.headers, stale.publicKeys,
                VerifyWebhookSignatureOptions.builder().now(stale.now).toleranceSeconds(301).build()))
                .doesNotThrowAnyException();
    }

    @Test void rejectsANullBody() {
        WebhookVectors.Case vector = WebhookVectors.byName("valid single signature");
        assertThatThrownBy(() -> WebhookVerifier.verifyWebhookSignature(
                (byte[]) null, vector.headers, vector.publicKeys, at(vector.now)))
                .isInstanceOfSatisfying(WebhookVerificationException.class,
                        e -> assertThat(e.getReason()).isEqualTo(Reason.INVALID_PAYLOAD));
    }

    private static void assertReason(String vectorName, Reason reason) {
        WebhookVectors.Case vector = WebhookVectors.byName(vectorName);
        assertThatThrownBy(() -> WebhookVerifier.verifyWebhookSignature(
                vector.body, vector.headers, vector.publicKeys, at(vector.now)))
                .as(vectorName)
                .isInstanceOfSatisfying(WebhookVerificationException.class,
                        e -> assertThat(e.getReason()).isEqualTo(reason));
    }
}
