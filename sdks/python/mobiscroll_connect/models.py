"""Typed response models.

Models are frozen dataclasses (immutable, hashable, light) — no Pydantic
dependency. ``from_dict`` constructors handle the API's camelCase wire format
and tolerate missing optional fields.
"""

from __future__ import annotations

from collections.abc import Iterator, Mapping
from dataclasses import dataclass, field
from datetime import datetime, timezone
from enum import Enum
from typing import Any


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
    expires_in: int | None = None
    refresh_token: str | None = None

    def to_dict(self) -> dict[str, Any]:
        return {
            "access_token": self.access_token,
            "token_type": self.token_type,
            "expires_in": self.expires_in,
            "refresh_token": self.refresh_token,
        }

    @classmethod
    def from_dict(cls, data: Mapping[str, Any]) -> TokenResponse:
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
    def from_dict(cls, data: Mapping[str, Any]) -> Calendar:
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
    status: str | None = None
    organizer: bool | None = None

    @classmethod
    def from_dict(cls, data: Mapping[str, Any]) -> EventAttendee:
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
    recurring_event_id: str | None = None
    color: str | None = None
    location: str | None = None
    attendees: list[EventAttendee] | None = None
    custom: Mapping[str, Any] | None = None
    conference: str | None = None
    availability: str | None = None
    privacy: str | None = None
    status: str | None = None
    link: str | None = None
    original: Mapping[str, Any] = field(default_factory=dict)
    description: str | None = None
    conference_data: Mapping[str, Any] | None = None
    last_modified: str | None = None

    @classmethod
    def from_dict(cls, data: Mapping[str, Any]) -> CalendarEvent:
        attendees_raw = data.get("attendees")
        attendees: list[EventAttendee] | None = None
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
            attendees=attendees,
            custom=data.get("custom"),
            conference=data.get("conference"),
            availability=data.get("availability"),
            privacy=data.get("privacy"),
            status=data.get("status"),
            link=data.get("link"),
            original=data.get("original", {}) or {},
            description=data.get("description"),
            conference_data=data.get("conferenceData"),
            last_modified=data.get("lastModified"),
        )


@dataclass(frozen=True)
class EventsListResponse:
    """Paginated events response.

    Iterate ``events`` directly, then call ``client.events.list(next_page_token=...)``
    to fetch the next page if ``next_page_token`` is set.
    """

    events: list[CalendarEvent]
    page_size: int | None = None
    next_page_token: str | None = None

    def __iter__(self) -> Iterator[CalendarEvent]:
        return iter(self.events)

    def __len__(self) -> int:
        return len(self.events)

    @property
    def has_more(self) -> bool:
        return bool(self.next_page_token)

    @classmethod
    def from_dict(cls, data: Mapping[str, Any]) -> EventsListResponse:
        events_raw = data.get("events", []) or []
        return cls(
            events=[CalendarEvent.from_dict(e) for e in events_raw],
            page_size=data.get("pageSize"),
            next_page_token=data.get("nextPageToken"),
        )


@dataclass(frozen=True)
class ConnectedAccount:
    """One connected calendar account.

    ``granted_scopes`` are the scopes the provider actually granted, which are not
    necessarily the ones Connect asked for: Google's consent screen lets the user untick
    the calendar permission and still complete sign-in.

    ``calendar_permission_granted`` is ``False`` for exactly those accounts — connected,
    but no calendars can be read from them until the user reconnects and allows access.
    It is ``None`` when the question does not apply (Apple and CalDav authenticate with a
    username and app password) or no scopes were recorded for the account.

    ``sync_state`` answers a different question: whether the stored credentials still
    work. It is ``"reauth_required"`` once the provider has rejected them, and
    ``"active"`` otherwise. ``calendar_permission_granted`` records what was agreed at
    consent time and never changes afterwards, so it cannot report a revoked grant.
    Neither can be repaired server-side — the user must run the connect flow again.
    """

    id: str
    display: str | None = None
    granted_scopes: list[str] = field(default_factory=list)
    calendar_permission_granted: bool | None = None
    sync_state: str = "active"
    sync_state_updated_at: str | None = None

    @classmethod
    def from_dict(cls, data: Mapping[str, Any]) -> ConnectedAccount:
        return cls(
            id=_require(data, "id"),
            display=data.get("display"),
            granted_scopes=[str(s) for s in (data.get("grantedScopes") or [])],
            calendar_permission_granted=data.get("calendarPermissionGranted"),
            sync_state=data.get("syncState") or "active",
            sync_state_updated_at=data.get("syncStateUpdatedAt"),
        )


@dataclass(frozen=True)
class ConnectionStatusResponse:
    connections: dict[str, list[ConnectedAccount]]
    limit_reached: bool = False

    @classmethod
    def from_dict(cls, data: Mapping[str, Any]) -> ConnectionStatusResponse:
        raw: Mapping[str, Any] = data.get("connections", {}) or {}
        connections: dict[str, list[ConnectedAccount]] = {}
        for provider, accounts in raw.items():
            connections[provider] = [
                ConnectedAccount.from_dict(a) if isinstance(a, Mapping) else ConnectedAccount(id=str(a))
                for a in (accounts or [])
            ]
        return cls(
            connections=connections,
            limit_reached=bool(data.get("limitReached", False)),
        )


@dataclass(frozen=True)
class DisconnectResponse:
    success: bool
    message: str | None = None

    @classmethod
    def from_dict(cls, data: Mapping[str, Any]) -> DisconnectResponse:
        return cls(
            success=bool(data.get("success", False)),
            message=data.get("message"),
        )


@dataclass(frozen=True)
class WebhookSubscription:
    """The provider-side subscription created by ``POST /subscribe-webhook``."""

    channel_id: str
    resource_id: str | None = None
    expiration: str | None = None

    @classmethod
    def from_dict(cls, data: Mapping[str, Any]) -> WebhookSubscription:
        return cls(
            channel_id=_require(data, "channelId"),
            resource_id=data.get("resourceId"),
            expiration=data.get("expiration"),
        )


@dataclass(frozen=True)
class SubscribeWebhookResponse:
    success: bool
    provider: str
    subscription: WebhookSubscription
    server_webhook_url: str
    channel_id: str

    @classmethod
    def from_dict(cls, data: Mapping[str, Any]) -> SubscribeWebhookResponse:
        subscription_raw = data.get("subscription")
        return cls(
            success=bool(data.get("success", False)),
            provider=data.get("provider", ""),
            subscription=WebhookSubscription.from_dict(
                subscription_raw if isinstance(subscription_raw, Mapping) else {}
            ),
            server_webhook_url=data.get("serverWebhookUrl", ""),
            channel_id=data.get("channelId", ""),
        )


@dataclass(frozen=True)
class UnsubscribeWebhookResponse:
    """Response from ``POST /unsubscribe-webhook``.

    ``success`` is ``True`` even when the provider-side unsubscribe itself failed
    (e.g. an already-expired subscription) — the server removes its local mapping
    regardless and explains the situation in ``message``. Treat a 200 response as
    final either way.
    """

    success: bool
    message: str | None = None

    @classmethod
    def from_dict(cls, data: Mapping[str, Any]) -> UnsubscribeWebhookResponse:
        return cls(
            success=bool(data.get("success", False)),
            message=data.get("message"),
        )
