"""Response parsers shared by the sync and async clients."""

from __future__ import annotations

from collections.abc import Mapping
from typing import Any

from ..exceptions import ServerError
from ..models import CalendarEvent


def extract_written_event(response: Any, operation: str) -> CalendarEvent:
    """Parse the event returned by ``POST``/``PUT /event``.

    The API returns the event itself; an ``{"event": ...}`` envelope is accepted too.
    """
    if isinstance(response, Mapping):
        wrapped = response.get("event")
        if isinstance(wrapped, Mapping):
            return CalendarEvent.from_dict(wrapped)
        if "id" in response and "provider" in response:
            return CalendarEvent.from_dict(response)
    msg = response.get("message") if isinstance(response, Mapping) else None
    raise ServerError(msg if isinstance(msg, str) else f"Failed to {operation} event", 400)
