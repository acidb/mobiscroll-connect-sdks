"""Maps HTTP responses to typed SDK exceptions. Shared by sync and async clients."""

from __future__ import annotations

from typing import Any, Mapping, Optional

import httpx

from ..exceptions import (
    AuthenticationError,
    MobiscrollConnectError,
    NetworkError,
    NotFoundError,
    RateLimitError,
    ServerError,
    ValidationError,
)


def _safe_json(response: httpx.Response) -> Mapping[str, Any]:
    try:
        data = response.json()
        return data if isinstance(data, Mapping) else {}
    except Exception:
        return {}


def map_response_error(response: httpx.Response) -> MobiscrollConnectError:
    """Map an HTTP response to a typed SDK exception."""
    status = response.status_code
    data = _safe_json(response)
    message = str(data.get("message") or response.reason_phrase or f"HTTP {status}")

    if status in (401, 403):
        return AuthenticationError(message)
    if status == 404:
        return NotFoundError(message)
    if status in (400, 422):
        details = data.get("details") if isinstance(data, Mapping) else None
        return ValidationError(message, details if isinstance(details, Mapping) else None)
    if status == 429:
        retry_after_raw = response.headers.get("retry-after")
        retry_after: Optional[int] = None
        if retry_after_raw is not None:
            try:
                retry_after = int(retry_after_raw)
            except (TypeError, ValueError):
                retry_after = None
        return RateLimitError(message, retry_after)
    if status in (500, 502, 503, 504):
        return ServerError(message, status)

    return MobiscrollConnectError(message, code=str(data.get("code") or "UNKNOWN_ERROR"))


def map_transport_error(exc: httpx.HTTPError) -> NetworkError:
    return NetworkError(str(exc) or exc.__class__.__name__)
