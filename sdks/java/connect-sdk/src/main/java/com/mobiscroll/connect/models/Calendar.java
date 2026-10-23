package com.mobiscroll.connect.models;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.mobiscroll.connect.Provider;

import java.util.HashMap;
import java.util.Map;

/** A calendar resource exposed by one of the supported providers. */
public final class Calendar {

    private final String id;
    private final Provider provider;
    private final String title;
    private final String timeZone;
    private final String color;
    private final String description;
    private final JsonNode original;
    private final String account;
    private final Boolean readOnly;
    private final Map<String, Object> additional = new HashMap<>();

    @JsonCreator
    public Calendar(
            @JsonProperty("id") String id,
            @JsonProperty("provider") Provider provider,
            @JsonProperty("title") String title,
            @JsonProperty("timeZone") String timeZone,
            @JsonProperty("color") String color,
            @JsonProperty("description") String description,
            @JsonProperty("original") JsonNode original,
            @JsonProperty("account") String account,
            @JsonProperty("readOnly") Boolean readOnly) {
        this.id = id;
        this.provider = provider;
        this.title = title;
        this.timeZone = timeZone;
        this.color = color;
        this.description = description;
        this.original = original;
        this.account = account;
        this.readOnly = readOnly;
    }

    /** Creates a calendar without {@code account} and {@code readOnly}. */
    public Calendar(String id, Provider provider, String title, String timeZone, String color, String description,
            JsonNode original) {
        this(id, provider, title, timeZone, color, description, original, null, null);
    }

    public String getId() { return id; }
    public Provider getProvider() { return provider; }
    public String getTitle() { return title; }
    public String getTimeZone() { return timeZone; }
    public String getColor() { return color; }
    public String getDescription() { return description; }
    public JsonNode getOriginal() { return original; }
    /** The connected account this calendar belongs to. */
    public String getAccount() { return account; }
    /** {@code true} when the authenticated connection cannot write to this calendar. */
    public Boolean getReadOnly() { return readOnly; }

    @JsonAnyGetter
    public Map<String, Object> getAdditional() { return additional; }

    @JsonAnySetter
    public void setAdditional(String key, Object value) { additional.put(key, value); }
}
