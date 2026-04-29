from datetime import datetime, timezone

import pytest

from mobiscroll_connect.models import (
    Calendar,
    CalendarEvent,
    ConnectionStatusResponse,
    DisconnectResponse,
    EventsListResponse,
    TokenResponse,
)


class TestTokenResponse:
    def test_from_dict_minimal(self):
        t = TokenResponse.from_dict({"access_token": "x"})
        assert t.access_token == "x"
        assert t.token_type == "Bearer"
        assert t.refresh_token is None

    def test_from_dict_full(self):
        t = TokenResponse.from_dict(
            {"access_token": "a", "token_type": "Bearer", "expires_in": 3600, "refresh_token": "r"}
        )
        assert t.expires_in == 3600
        assert t.refresh_token == "r"

    def test_from_dict_missing_required(self):
        with pytest.raises(ValueError, match="access_token"):
            TokenResponse.from_dict({})

    def test_to_dict_roundtrip(self):
        t = TokenResponse(access_token="a", refresh_token="r")
        assert t.to_dict()["access_token"] == "a"


class TestCalendar:
    def test_from_dict(self):
        c = Calendar.from_dict(
            {"provider": "google", "id": "primary", "title": "My calendar", "timeZone": "UTC"}
        )
        assert c.provider == "google"
        assert c.time_zone == "UTC"

    def test_required_fields(self):
        with pytest.raises(ValueError):
            Calendar.from_dict({})


class TestCalendarEvent:
    def test_basic(self):
        e = CalendarEvent.from_dict({
            "provider": "google",
            "id": "evt-1",
            "calendarId": "primary",
            "title": "Meeting",
            "start": "2024-06-15T10:00:00Z",
            "end": "2024-06-15T11:00:00Z",
            "allDay": False,
        })
        assert e.calendar_id == "primary"
        assert e.start == datetime(2024, 6, 15, 10, 0, 0, tzinfo=timezone.utc)
        assert e.all_day is False

    def test_attendees(self):
        e = CalendarEvent.from_dict({
            "provider": "google",
            "id": "1",
            "calendarId": "primary",
            "attendees": [{"email": "a@b", "status": "accepted"}],
        })
        assert e.attendees is not None
        assert e.attendees[0].email == "a@b"


class TestEventsListResponse:
    def test_iter_and_len(self):
        r = EventsListResponse.from_dict({
            "events": [
                {"provider": "google", "id": "1", "calendarId": "primary"},
                {"provider": "google", "id": "2", "calendarId": "primary"},
            ],
            "nextPageToken": "next",
        })
        assert len(r) == 2
        assert r.has_more is True
        assert [e.id for e in r] == ["1", "2"]

    def test_empty(self):
        r = EventsListResponse.from_dict({})
        assert len(r) == 0
        assert r.has_more is False


class TestConnectionStatusResponse:
    def test_from_dict(self):
        r = ConnectionStatusResponse.from_dict({
            "connections": {
                "google": [{"id": "user@gmail.com", "display": "User"}],
                "microsoft": [],
            },
            "limitReached": True,
            "limit": 5,
        })
        assert r.connections["google"][0].id == "user@gmail.com"
        assert r.connections["microsoft"] == []
        assert r.limit_reached is True
        assert r.limit == 5


class TestDisconnectResponse:
    def test_from_dict(self):
        r = DisconnectResponse.from_dict({"success": True})
        assert r.success is True
        assert r.message is None
