from __future__ import annotations

from collections.abc import Iterable, Iterator, Mapping
from typing import Any

from .._internal.payloads import (
    DateLike,
    build_delete_query,
    build_event_payload,
    build_list_events_query,
)
from .._internal.responses import extract_written_event
from ..api_client import ApiClient
from ..exceptions import ServerError
from ..models import CalendarEvent, EventsListResponse


class Events:
    """Event CRUD across all connected providers."""

    def __init__(self, api_client: ApiClient) -> None:
        self._api = api_client

    def list(
        self,
        *,
        start: DateLike | None = None,
        end: DateLike | None = None,
        calendar_ids: Mapping[str, Iterable[str]] | None = None,
        page_size: int | None = None,
        next_page_token: str | None = None,
        single_events: bool | None = None,
        include_cancelled: bool | None = None,
        event_ids: Mapping[str, Mapping[str, Iterable[str]]] | None = None,
    ) -> EventsListResponse:
        """List events.

        ``calendar_ids`` is a mapping from provider name to calendar IDs, e.g.::

            {"google": ["primary"], "microsoft": ["calendar-id-1"]}

        ``page_size`` is capped at 1000 server-side.

        ``single_events`` (default ``True``) expands recurring events into their
        occurrences within ``start`` and ``end``; ``False`` returns each series once, as
        its master event.

        ``include_cancelled``: with ``single_events=True``, also returns the deleted
        occurrences of series that still exist, as events with ``status == "cancelled"``.
        Default ``False``. Ignored with ``single_events=False``. Applies to the normal list
        and to ``event_ids``.

        ``event_ids`` returns only these events, given as event ids per provider and
        calendar id, and sent JSON-encoded like ``calendar_ids``::

            {"google": {"ana@example.com": ["abc123", "def456"]}}

        At most 40 ids per request (more is rejected with a ``ValidationError``). Cannot be
        combined with ``calendar_ids`` (``ValidationError``) and is not available with the
        ``free-busy`` scope (HTTP 403, an ``AuthenticationError``).

        With ``single_events=False``, each series master or single event comes back (never an
        occurrence, modified or not), in the order of the ids, without a time window. With
        ``single_events=True`` (default), each id returns what the list returns for it
        inside ``start`` and ``end``: the occurrences of a series, never its master, or a
        single event when it overlaps the window. Missing or deleted ids are left out,
        without an error.

        A ``next_page_token`` from such a request is only valid with the same ``event_ids``,
        ``single_events``, ``include_cancelled``, ``start`` and ``end``.

        The response's ``info`` carries the providers that failed and the calendars no
        connected account can read.
        """
        query = build_list_events_query(
            start=start,
            end=end,
            calendar_ids=calendar_ids,
            page_size=page_size,
            next_page_token=next_page_token,
            single_events=single_events,
            include_cancelled=include_cancelled,
            event_ids=event_ids,
        )
        data = self._api.get("events", params=query or None)
        return EventsListResponse.from_dict(data if isinstance(data, Mapping) else {})

    def iter_all(
        self,
        *,
        start: DateLike | None = None,
        end: DateLike | None = None,
        calendar_ids: Mapping[str, Iterable[str]] | None = None,
        page_size: int | None = None,
        single_events: bool | None = None,
        include_cancelled: bool | None = None,
        event_ids: Mapping[str, Mapping[str, Iterable[str]]] | None = None,
    ) -> Iterator[CalendarEvent]:
        """Yield every event across all pages, transparently following
        ``next_page_token``. Callers don't need to manage pagination state."""
        token: str | None = None
        while True:
            page = self.list(
                start=start,
                end=end,
                calendar_ids=calendar_ids,
                page_size=page_size,
                next_page_token=token,
                single_events=single_events,
                include_cancelled=include_cancelled,
                event_ids=event_ids,
            )
            yield from page.events
            if not page.next_page_token:
                return
            token = page.next_page_token

    def create(self, event: Mapping[str, Any]) -> CalendarEvent:
        """Create an event. Required keys: ``provider``, ``calendar_id`` (or
        ``calendarId``), ``title``, ``start``, ``end``."""
        payload = build_event_payload(event)
        response = self._api.post("event", json=payload)
        return extract_written_event(response, "create")

    def update(self, event: Mapping[str, Any]) -> CalendarEvent:
        """Update an event. Required keys: ``provider``, ``calendar_id``,
        ``event_id``."""
        payload = build_event_payload(event)
        response = self._api.put("event", json=payload)
        return extract_written_event(response, "update")

    def delete(self, params: Mapping[str, Any]) -> None:
        """Delete an event. Required keys: ``provider``, ``calendar_id``,
        ``event_id``. Optional: ``recurring_event_id``, ``delete_mode``."""
        query = build_delete_query(params)
        response = self._api.delete("event", params=query)

        if isinstance(response, Mapping) and response.get("success") is False:
            raise ServerError(str(response.get("message") or "Failed to delete event"), 400)

