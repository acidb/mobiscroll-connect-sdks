package com.mobiscroll.connect.internal;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds URL query strings. Booleans are encoded as the strings {@code "true"} / {@code "false"}
 * to match the wire format used by the Node, PHP, and .NET SDKs.
 */
public final class QueryStringBuilder {

    private final Map<String, List<String>> params = new LinkedHashMap<>();

    public QueryStringBuilder add(String key, Object value) {
        if (value == null) {
            return this;
        }
        if (value instanceof Boolean) {
            addOne(key, ((Boolean) value) ? "true" : "false");
        } else if (value instanceof OffsetDateTime) {
            addOne(key, ((OffsetDateTime) value).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        } else if (value instanceof Collection) {
            for (Object v : (Collection<?>) value) {
                if (v != null) {
                    addOne(key, String.valueOf(v));
                }
            }
        } else {
            addOne(key, String.valueOf(value));
        }
        return this;
    }

    private void addOne(String key, String value) {
        params.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
    }

    public boolean isEmpty() {
        return params.isEmpty();
    }

    /** @return the encoded query string without a leading {@code ?}. */
    public String build() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> e : params.entrySet()) {
            for (String v : e.getValue()) {
                if (sb.length() > 0) {
                    sb.append('&');
                }
                sb.append(encode(e.getKey())).append('=').append(encode(v));
            }
        }
        return sb.toString();
    }

    private static String encode(String s) {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
