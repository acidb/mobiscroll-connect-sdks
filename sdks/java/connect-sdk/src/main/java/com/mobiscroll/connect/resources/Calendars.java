package com.mobiscroll.connect.resources;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mobiscroll.connect.ApiClient;
import com.mobiscroll.connect.models.Calendar;

/** Calendar resource. */
public final class Calendars {

    private final ApiClient api;

    public Calendars(ApiClient api) {
        this.api = api;
    }

    /** List calendars across all connected providers. */
    public List<Calendar> list() {
        return api.get("/calendars", null, new TypeReference<List<Calendar>>() {});
    }
}
