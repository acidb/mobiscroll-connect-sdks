"""Pure functions that build request payloads / queries from caller input.

Kept separate from the API client so they can be unit-tested in isolation and
reused by both the sync and async resources.
"""

from __future__ import annotations

import json
from datetime import datetime, timezone
from typing import Any, Dict, Iterable, Mapping, Optional, Union

DateLike = Union[datetime, str]

_LIST_QUERY_KEYS_PASSTHROUGH = ("provider", "calendarId", "eventId", "recurringEventId", "deleteMode")


def format_datetime(value: DateLike) -> str:
    """Convert to ISO 8601 with a ``Z`` suffix when UTC. String input is passed through."""
    if isinstance(value, datetime):
        if value.tzinfo is None:
            value = value.replace(tzinfo=timezone.utc)
        iso = value.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        return iso
    return str(value)


def build_list_events_query(
    *,
    start: Optional[DateLike] = None,
    end: Optional[DateLike] = None,
    calendar_ids: Optional[Mapping[str, Iterable[str]]] = None,
    page_size: Optional[int] = None,
    next_page_token: Optional[str] = None,
    single_events: Optional[bool] = None,
) -> Dict[str, str]:
    """Build the query string for ``GET /events``. All values are stringified."""
    query: Dict[str, str] = {}

    if start is not None:
        query["start"] = format_datetime(start)
    if end is not None:
        query["end"] = format_datetime(end)
    if calendar_ids is not None:
        # Convert iterables to lists so json.dumps emits arrays.
        normalized = {k: list(v) for k, v in calendar_ids.items()}
        query["calendarIds"] = json.dumps(normalized, separators=(",", ":"))
    if page_size is not None:
        query["pageSize"] = str(min(int(page_size), 1000))
    if next_page_token is not None:
        query["nextPageToken"] = str(next_page_token)
    if single_events is not None:
        # API expects string booleans in query string.
        query["singleEvents"] = "true" if single_events else "false"

    return query


def build_event_payload(event: Mapping[str, Any]) -> Dict[str, Any]:
    """Normalize a snake_case-friendly event dict into the API's camelCase shape.

    Accepts both wire-format keys (``calendarId``, ``allDay``) and Pythonic ones
    (``calendar_id``, ``all_day``). Datetime fields are formatted to ISO 8601.
    """
    snake_to_camel = {
        "calendar_id": "calendarId",
        "event_id": "eventId",
        "recurring_event_id": "recurringEventId",
        "update_mode": "updateMode",
        "delete_mode": "deleteMode",
        "all_day": "allDay",
    }

    payload: Dict[str, Any] = {}
    for key, value in event.items():
        out_key = snake_to_camel.get(key, key)
        if out_key in ("start", "end") and value is not None:
            payload[out_key] = format_datetime(value)
        else:
            payload[out_key] = value

    return payload


def build_delete_query(params: Mapping[str, Any]) -> Dict[str, str]:
    """Build the query string for ``DELETE /event``. Validates required keys."""
    payload = build_event_payload(params)
    query: Dict[str, str] = {}
    for key in _LIST_QUERY_KEYS_PASSTHROUGH:
        value = payload.get(key)
        if value is not None and value != "":
            query[key] = str(value)

    snake_for_error = {"provider": "provider", "calendarId": "calendar_id", "eventId": "event_id"}
    for required in ("provider", "calendarId", "eventId"):
        if required not in query:
            raise ValueError(f"{snake_for_error[required]} is required for event deletion")

    return query
