from __future__ import annotations

from typing import Any, Iterable, Iterator, Mapping, Optional

from .._internal.payloads import (
    DateLike,
    build_delete_query,
    build_event_payload,
    build_list_events_query,
)
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
        start: Optional[DateLike] = None,
        end: Optional[DateLike] = None,
        calendar_ids: Optional[Mapping[str, Iterable[str]]] = None,
        page_size: Optional[int] = None,
        next_page_token: Optional[str] = None,
        single_events: Optional[bool] = None,
    ) -> EventsListResponse:
        """List events.

        ``calendar_ids`` is a mapping from provider name to calendar IDs, e.g.::

            {"google": ["primary"], "microsoft": ["calendar-id-1"]}

        ``page_size`` is capped at 1000 server-side.
        """
        query = build_list_events_query(
            start=start,
            end=end,
            calendar_ids=calendar_ids,
            page_size=page_size,
            next_page_token=next_page_token,
            single_events=single_events,
        )
        data = self._api.get("events", params=query or None)
        return EventsListResponse.from_dict(data if isinstance(data, Mapping) else {})

    def iter_all(
        self,
        *,
        start: Optional[DateLike] = None,
        end: Optional[DateLike] = None,
        calendar_ids: Optional[Mapping[str, Iterable[str]]] = None,
        page_size: Optional[int] = None,
        single_events: Optional[bool] = None,
    ) -> Iterator[CalendarEvent]:
        """Yield every event across all pages, transparently following
        ``next_page_token``. Callers don't need to manage pagination state."""
        token: Optional[str] = None
        while True:
            page = self.list(
                start=start,
                end=end,
                calendar_ids=calendar_ids,
                page_size=page_size,
                next_page_token=token,
                single_events=single_events,
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
        return self._extract_event(response, "create")

    def update(self, event: Mapping[str, Any]) -> CalendarEvent:
        """Update an event. Required keys: ``provider``, ``calendar_id``,
        ``event_id``."""
        payload = build_event_payload(event)
        response = self._api.put("event", json=payload)
        return self._extract_event(response, "update")

    def delete(self, params: Mapping[str, Any]) -> None:
        """Delete an event. Required keys: ``provider``, ``calendar_id``,
        ``event_id``. Optional: ``recurring_event_id``, ``delete_mode``."""
        query = build_delete_query(params)
        response = self._api.delete("event", params=query)

        if isinstance(response, Mapping) and response.get("success") is False:
            raise ServerError(str(response.get("message") or "Failed to delete event"), 400)

    @staticmethod
    def _extract_event(response: Any, operation: str) -> CalendarEvent:
        if isinstance(response, Mapping) and isinstance(response.get("event"), Mapping):
            return CalendarEvent.from_dict(response["event"])
        message = (
            response.get("message")
            if isinstance(response, Mapping) and isinstance(response.get("message"), str)
            else f"Failed to {operation} event"
        )
        raise ServerError(message, 400)
