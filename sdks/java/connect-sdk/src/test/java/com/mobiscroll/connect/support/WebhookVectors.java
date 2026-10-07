package com.mobiscroll.connect.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** The shared cross-SDK webhook signature vectors in {@code fixtures/webhook-vectors.json}. */
public final class WebhookVectors {

    private static final JsonNode ROOT = load();

    private WebhookVectors() {}

    /** One signed delivery and whether it must verify. */
    public static final class Case {
        public final String name;
        public final boolean valid;
        public final Map<String, String> headers;
        public final String body;
        public final List<String> publicKeys;
        public final long now;

        Case(JsonNode node) {
            this.name = node.get("name").asText();
            this.valid = node.get("valid").asBoolean();
            this.headers = new LinkedHashMap<>();
            node.get("headers").fields().forEachRemaining(e -> headers.put(e.getKey(), e.getValue().asText()));
            this.body = node.get("body").asText();
            this.publicKeys = new ArrayList<>();
            node.get("publicKeys").forEach(k -> publicKeys.add(k.asText()));
            this.now = node.get("now").asLong();
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static List<Case> cases() {
        List<Case> cases = new ArrayList<>();
        ROOT.get("cases").forEach(node -> cases.add(new Case(node)));
        return cases;
    }

    public static Case byName(String name) {
        return cases().stream()
                .filter(c -> c.name.equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing vector: " + name));
    }

    /** {@code active}, {@code previous} or {@code unrelated}. */
    public static String key(String id) {
        return ROOT.get("keys").get(id).asText();
    }

    private static JsonNode load() {
        try (InputStream in = WebhookVectors.class.getResourceAsStream("/fixtures/webhook-vectors.json")) {
            return new ObjectMapper().readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
