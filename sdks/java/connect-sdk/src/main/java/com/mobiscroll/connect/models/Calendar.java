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
    private final Map<String, Object> additional = new HashMap<>();

    @JsonCreator
    public Calendar(
            @JsonProperty("id") String id,
            @JsonProperty("provider") Provider provider,
            @JsonProperty("title") String title,
            @JsonProperty("timeZone") String timeZone,
            @JsonProperty("color") String color,
            @JsonProperty("description") String description,
            @JsonProperty("original") JsonNode original) {
        this.id = id;
        this.provider = provider;
        this.title = title;
        this.timeZone = timeZone;
        this.color = color;
        this.description = description;
        this.original = original;
    }

    public String getId() { return id; }
    public Provider getProvider() { return provider; }
    public String getTitle() { return title; }
    public String getTimeZone() { return timeZone; }
    public String getColor() { return color; }
    public String getDescription() { return description; }
    public JsonNode getOriginal() { return original; }

    @JsonAnyGetter
    public Map<String, Object> getAdditional() { return additional; }

    @JsonAnySetter
    public void setAdditional(String key, Object value) { additional.put(key, value); }
}
