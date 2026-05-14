package com.mobiscroll.connect.resources;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.mobiscroll.connect.ApiClient;
import com.mobiscroll.connect.Provider;
import com.mobiscroll.connect.exceptions.MobiscrollConnectException;
import com.mobiscroll.connect.internal.JsonMapperHolder;
import com.mobiscroll.connect.internal.QueryStringBuilder;
import com.mobiscroll.connect.models.CalendarEvent;
import com.mobiscroll.connect.models.EventCreateData;
import com.mobiscroll.connect.models.EventDeleteParams;
import com.mobiscroll.connect.models.EventListParams;
import com.mobiscroll.connect.models.EventUpdateData;
import com.mobiscroll.connect.models.EventsListResponse;

/** Events resource: list, create, update, delete. */
public final class Events {

    private final ApiClient api;

    public Events(ApiClient api) {
        this.api = api;
    }

    /**
     * List events across the supplied calendars (or all connected calendars if none specified).
     * The {@code calendarIds} map is JSON-encoded into a single query parameter, matching the
     * wire format used by the other SDKs (e.g. {@code calendarIds={"google":["primary"]}}).
     */
    public EventsListResponse list(EventListParams params) {
        QueryStringBuilder qs = new QueryStringBuilder();
        if (params != null) {
            Map<Provider, List<String>> ids = params.getCalendarIds();
            if (ids != null && !ids.isEmpty()) {
                Map<String, List<String>> wire = new LinkedHashMap<>();
                for (Map.Entry<Provider, List<String>> e : ids.entrySet()) {
                    wire.put(e.getKey().wireValue(), e.getValue());
                }
                try {
                    qs.add("calendarIds", JsonMapperHolder.get().writeValueAsString(wire));
                } catch (JsonProcessingException e) {
                    throw new MobiscrollConnectException("Failed to encode calendarIds", e);
                }
            }
            qs.add("start", params.getStart())
              .add("end", params.getEnd())
              .add("pageSize", params.getPageSize())
              .add("nextPageToken", params.getNextPageToken())
              .add("singleEvents", params.getSingleEvents());
        }
        return api.get("/events", qs.isEmpty() ? null : qs.build(),
                new TypeReference<EventsListResponse>() {});
    }

    /** Create an event. Uses singular {@code /event} path (matches server contract). */
    public CalendarEvent create(EventCreateData data) {
        return api.post("/event", data, new TypeReference<CalendarEvent>() {});
    }

    /** Update an event. Uses singular {@code /event} path (matches server contract). */
    public CalendarEvent update(EventUpdateData data) {
        return api.put("/event", data, new TypeReference<CalendarEvent>() {});
    }

    /** Delete an event. Uses singular {@code /event} path (matches server contract). */
    public void delete(EventDeleteParams params) {
        QueryStringBuilder qs = new QueryStringBuilder()
                .add("provider", params.getProvider().wireValue())
                .add("calendarId", params.getCalendarId())
                .add("eventId", params.getEventId())
                .add("recurringEventId", params.getRecurringEventId())
                .add("deleteMode", params.getDeleteMode());
        api.delete("/event", qs.build());
    }
}
