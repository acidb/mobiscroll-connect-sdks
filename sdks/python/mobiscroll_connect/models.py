"""Typed response models.

Models are frozen dataclasses (immutable, hashable, light) — no Pydantic
dependency. ``from_dict`` constructors handle the API's camelCase wire format
and tolerate missing optional fields.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from enum import Enum
from typing import Any, Dict, List, Mapping, Optional


class Provider(str, Enum):
    """Supported calendar providers. Inherits from ``str`` so values
    serialize transparently into URL query strings and JSON bodies."""

    GOOGLE = "google"
    MICROSOFT = "microsoft"
    APPLE = "apple"
    CALDAV = "caldav"


def _parse_datetime(value: Any) -> datetime:
    if isinstance(value, datetime):
        return value
    if not value:
        return datetime.now(tz=timezone.utc)
    s = str(value)
    # Python <3.11 doesn't accept "Z" suffix in fromisoformat.
    if s.endswith("Z"):
        s = s[:-1] + "+00:00"
    try:
        return datetime.fromisoformat(s)
    except ValueError:
        return datetime.now(tz=timezone.utc)


def _require(data: Mapping[str, Any], key: str) -> Any:
    if key not in data or data[key] is None:
        raise ValueError(f"{key} is required")
    return data[key]


@dataclass(frozen=True)
class TokenResponse:
    """OAuth2 token bundle returned by ``/oauth/token``."""

    access_token: str
    token_type: str = "Bearer"
    expires_in: Optional[int] = None
    refresh_token: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        return {
            "access_token": self.access_token,
            "token_type": self.token_type,
            "expires_in": self.expires_in,
            "refresh_token": self.refresh_token,
        }

    @classmethod
    def from_dict(cls, data: Mapping[str, Any]) -> "TokenResponse":
        return cls(
            access_token=_require(data, "access_token"),
            token_type=data.get("token_type") or "Bearer",
            expires_in=data.get("expires_in"),
            refresh_token=data.get("refresh_token"),
        )


@dataclass(frozen=True)
class Calendar:
    """A calendar entry returned by ``GET /calendars``."""

    provider: str
    id: str
    title: str = ""
    time_zone: str = "UTC"
    color: str = ""
    description: str = ""
    original: Mapping[str, Any] = field(default_factory=dict)

    @classmethod
    def from_dict(cls, data: Mapping[str, Any]) -> "Calendar":
        return cls(
            provider=_require(data, "provider"),
            id=_require(data, "id"),
            title=data.get("title", ""),
            time_zone=data.get("timeZone", "UTC"),
            color=data.get("color", ""),
            description=data.get("description", ""),
            original=data.get("original", {}) or {},
        )


@dataclass(frozen=True)
class EventAttendee:
    email: str
    status: Optional[str] = None
    organizer: Optional[bool] = None

    @classmethod
    def from_dict(cls, data: Mapping[str, Any]) -> "EventAttendee":
        return cls(
            email=_require(data, "email"),
            status=data.get("status"),
            organizer=data.get("organizer"),
        )


@dataclass(frozen=True)
class CalendarEvent:
    """An event from any calendar provider."""

    provider: str
    id: str
    calendar_id: str
    title: str
    start: datetime
    end: datetime
    all_day: bool = False
    recurring_event_id: Optional[str] = None
    color: Optional[str] = None
    location: Optional[str] = None
    description: Optional[str] = None
    attendees: Optional[List[EventAttendee]] = None
    custom: Optional[Mapping[str, Any]] = None
    conference: Optional[str] = None
    availability: Optional[str] = None
    privacy: Optional[str] = None
    status: Optional[str] = None
    link: Optional[str] = None
    original: Mapping[str, Any] = field(default_factory=dict)

    @classmethod
    def from_dict(cls, data: Mapping[str, Any]) -> "CalendarEvent":
        attendees_raw = data.get("attendees")
        attendees: Optional[List[EventAttendee]] = None
        if isinstance(attendees_raw, list):
            attendees = [
                EventAttendee.from_dict(a) if isinstance(a, Mapping) else EventAttendee(email=str(a))
                for a in attendees_raw
            ]

        return cls(
            provider=_require(data, "provider"),
            id=_require(data, "id"),
            calendar_id=_require(data, "calendarId"),
            title=data.get("title", ""),
            start=_parse_datetime(data.get("start")),
            end=_parse_datetime(data.get("end")),
            all_day=bool(data.get("allDay", False)),
            recurring_event_id=data.get("recurringEventId"),
            color=data.get("color"),
            location=data.get("location"),
            description=data.get("description"),
            attendees=attendees,
            custom=data.get("custom"),
            conference=data.get("conference"),
            availability=data.get("availability"),
            privacy=data.get("privacy"),
            status=data.get("status"),
            link=data.get("link"),
            original=data.get("original", {}) or {},
        )


@dataclass(frozen=True)
class EventsListResponse:
    """Paginated events response.

    Iterate ``events`` directly, then call ``client.events.list(next_page_token=...)``
    to fetch the next page if ``next_page_token`` is set.
    """

    events: List[CalendarEvent]
    page_size: Optional[int] = None
    next_page_token: Optional[str] = None

    def __iter__(self):
        return iter(self.events)

    def __len__(self) -> int:
        return len(self.events)

    @property
    def has_more(self) -> bool:
        return bool(self.next_page_token)

    @classmethod
    def from_dict(cls, data: Mapping[str, Any]) -> "EventsListResponse":
        events_raw = data.get("events", []) or []
        return cls(
            events=[CalendarEvent.from_dict(e) for e in events_raw],
            page_size=data.get("pageSize"),
            next_page_token=data.get("nextPageToken"),
        )


@dataclass(frozen=True)
class ConnectedAccount:
    id: str
    display: Optional[str] = None

    @classmethod
    def from_dict(cls, data: Mapping[str, Any]) -> "ConnectedAccount":
        return cls(id=_require(data, "id"), display=data.get("display"))


@dataclass(frozen=True)
class ConnectionStatusResponse:
    connections: Dict[str, List[ConnectedAccount]]
    limit_reached: bool = False
    limit: Optional[int] = None

    @classmethod
    def from_dict(cls, data: Mapping[str, Any]) -> "ConnectionStatusResponse":
        raw: Mapping[str, Any] = data.get("connections", {}) or {}
        connections: Dict[str, List[ConnectedAccount]] = {}
        for provider, accounts in raw.items():
            connections[provider] = [
                ConnectedAccount.from_dict(a) if isinstance(a, Mapping) else ConnectedAccount(id=str(a))
                for a in (accounts or [])
            ]
        return cls(
            connections=connections,
            limit_reached=bool(data.get("limitReached", False)),
            limit=data.get("limit"),
        )


@dataclass(frozen=True)
class DisconnectResponse:
    success: bool
    message: Optional[str] = None

    @classmethod
    def from_dict(cls, data: Mapping[str, Any]) -> "DisconnectResponse":
        return cls(
            success=bool(data.get("success", False)),
            message=data.get("message"),
        )
