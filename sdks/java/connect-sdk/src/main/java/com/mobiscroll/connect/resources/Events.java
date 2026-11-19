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
     *
     * <p>{@code singleEvents} (default {@code true}) expands recurring events into occurrences;
     * {@code false} returns the series masters. {@code includeCancelled} also returns deleted
     * occurrences, as events with {@code status} {@code "cancelled"}, with {@code singleEvents(true)}.
     * {@code eventIds} returns only the given events, at most 40 ids, and cannot be combined with
     * {@code calendarIds}; see {@link EventListParams#getEventIds()}. {@link EventsListResponse#getInfo()}
     * carries the providers that failed and the calendars no connected account can read.
     *
     * <pre>{@code
     * // Fetch two events by id, each as the event itself (a series' master, or an occurrence)
     * EventsListResponse result = client.events().list(EventListParams.builder()
     *     .eventIds(Map.of(Provider.GOOGLE, Map.of("ana@example.com", List.of("abc123", "def456"))))
     *     .singleEvents(false)
     *     .build());
     * }</pre>
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
              .add("singleEvents", params.getSingleEvents())
              .add("includeCancelled", params.getIncludeCancelled());
            // Sent whenever set, even empty ({}): the server answers 400, whereas dropping it would
            // turn the filter into a list of every event.
            Map<Provider, Map<String, List<String>>> eventIds = params.getEventIds();
            if (eventIds != null) {
                Map<String, Map<String, List<String>>> wire = new LinkedHashMap<>();
                for (Map.Entry<Provider, Map<String, List<String>>> e : eventIds.entrySet()) {
                    wire.put(e.getKey().wireValue(), e.getValue());
                }
                try {
                    qs.add("eventIds", JsonMapperHolder.get().writeValueAsString(wire));
                } catch (JsonProcessingException e) {
                    throw new MobiscrollConnectException("Failed to encode eventIds", e);
                }
            }
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
