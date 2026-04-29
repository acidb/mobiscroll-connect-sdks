import json
from datetime import datetime, timezone

import pytest

from mobiscroll_connect._internal.payloads import (
    build_delete_query,
    build_event_payload,
    build_list_events_query,
    format_datetime,
)


class TestFormatDatetime:
    def test_string_passthrough(self):
        assert format_datetime("2024-01-01T00:00:00Z") == "2024-01-01T00:00:00Z"

    def test_naive_datetime_treated_as_utc(self):
        d = datetime(2024, 6, 15, 10, 0, 0)
        assert format_datetime(d) == "2024-06-15T10:00:00Z"

    def test_aware_datetime_converted_to_utc(self):
        from datetime import timedelta, timezone as tz

        d = datetime(2024, 6, 15, 12, 0, 0, tzinfo=tz(timedelta(hours=2)))
        assert format_datetime(d) == "2024-06-15T10:00:00Z"


class TestBuildListEventsQuery:
    def test_empty(self):
        assert build_list_events_query() == {}

    def test_full(self):
        q = build_list_events_query(
            start=datetime(2024, 1, 1, tzinfo=timezone.utc),
            end="2024-01-31T00:00:00Z",
            calendar_ids={"google": ["primary"], "microsoft": ["a", "b"]},
            page_size=50,
            next_page_token="next",
            single_events=True,
        )
        assert q["start"] == "2024-01-01T00:00:00Z"
        assert q["end"] == "2024-01-31T00:00:00Z"
        # JSON-encoded calendar IDs
        decoded = json.loads(q["calendarIds"])
        assert decoded == {"google": ["primary"], "microsoft": ["a", "b"]}
        assert q["pageSize"] == "50"
        assert q["nextPageToken"] == "next"
        assert q["singleEvents"] == "true"

    def test_page_size_capped_at_1000(self):
        q = build_list_events_query(page_size=10000)
        assert q["pageSize"] == "1000"

    def test_single_events_false(self):
        q = build_list_events_query(single_events=False)
        assert q["singleEvents"] == "false"


class TestBuildEventPayload:
    def test_snake_case_to_camel(self):
        p = build_event_payload({
            "provider": "google",
            "calendar_id": "primary",
            "event_id": "evt-1",
            "all_day": True,
            "title": "x",
        })
        assert p["calendarId"] == "primary"
        assert p["eventId"] == "evt-1"
        assert p["allDay"] is True
        assert "calendar_id" not in p

    def test_camel_passthrough(self):
        p = build_event_payload({"calendarId": "primary", "title": "x"})
        assert p["calendarId"] == "primary"

    def test_datetime_formatting(self):
        p = build_event_payload({
            "calendar_id": "primary",
            "start": datetime(2024, 6, 15, 10, 0, 0),
            "end": "2024-06-15T11:00:00Z",
        })
        assert p["start"] == "2024-06-15T10:00:00Z"
        assert p["end"] == "2024-06-15T11:00:00Z"


class TestBuildDeleteQuery:
    def test_required_keys(self):
        q = build_delete_query({
            "provider": "google",
            "calendar_id": "primary",
            "event_id": "evt-1",
        })
        assert q == {"provider": "google", "calendarId": "primary", "eventId": "evt-1"}

    def test_optional_recurring(self):
        q = build_delete_query({
            "provider": "google",
            "calendar_id": "primary",
            "event_id": "evt-1",
            "recurring_event_id": "series",
            "delete_mode": "following",
        })
        assert q["recurringEventId"] == "series"
        assert q["deleteMode"] == "following"

    def test_missing_required_raises(self):
        with pytest.raises(ValueError, match="event_id is required"):
            build_delete_query({"provider": "google", "calendar_id": "primary"})
