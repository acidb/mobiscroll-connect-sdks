"""Exception hierarchy for the Mobiscroll Connect SDK.

All SDK errors inherit from :class:`MobiscrollConnectError` so callers can do a
broad ``except MobiscrollConnectError`` while keeping the option of catching
specific subclasses (e.g. :class:`AuthenticationError`).
"""

from __future__ import annotations

from collections.abc import Mapping, Sequence
from typing import Any, NamedTuple


class MobiscrollConnectError(Exception):
    """Base class for all SDK errors."""

    code: str = "MOBISCROLL_ERROR"

    def __init__(self, message: str = "", code: str | None = None) -> None:
        super().__init__(message)
        self.message = message
        if code is not None:
            self.code = code

    def __str__(self) -> str:
        return self.message or super().__str__()


class AuthenticationError(MobiscrollConnectError):
    """Raised on 401/403 responses or when token refresh fails."""

    code = "AUTHENTICATION_ERROR"

    def __init__(self, message: str = "Authentication failed") -> None:
        super().__init__(message)


class BlockedAccount(NamedTuple):
    """A connected account that withheld calendar access on its consent screen."""

    provider: str
    account: str


class CalendarPermissionError(AuthenticationError):
    """Raised when no connected account has the calendar access the request needs.

    The user completed sign-in but did not grant the calendar permission — Google's
    consent screen presents it as a separate checkbox. This cannot be repaired
    server-side, because providers only issue permissions at consent time: the
    accounts in ``accounts`` have to run the connect flow again and allow access.

    Subclasses :class:`AuthenticationError`, so existing handlers keep working.
    """

    code = "CALENDAR_PERMISSION_REQUIRED"

    def __init__(
        self,
        message: str = "No connected account has calendar access",
        accounts: Sequence[BlockedAccount] | None = None,
    ) -> None:
        super().__init__(message)
        self.accounts: list[BlockedAccount] = list(accounts or [])


class NotFoundError(MobiscrollConnectError):
    """Raised on 404 responses."""

    code = "NOT_FOUND_ERROR"

    def __init__(self, message: str = "Resource not found") -> None:
        super().__init__(message)


class ValidationError(MobiscrollConnectError):
    """Raised on 400/422 responses. ``details`` carries field-level errors."""

    code = "VALIDATION_ERROR"

    def __init__(
        self,
        message: str = "Validation failed",
        details: Mapping[str, Any] | None = None,
    ) -> None:
        super().__init__(message)
        self.details: Mapping[str, Any] = details or {}


class RateLimitError(MobiscrollConnectError):
    """Raised on 429 responses. ``retry_after`` is the Retry-After header in seconds."""

    code = "RATE_LIMIT_ERROR"

    def __init__(
        self,
        message: str = "Rate limit exceeded",
        retry_after: int | None = None,
    ) -> None:
        super().__init__(message)
        self.retry_after = retry_after


class ServerError(MobiscrollConnectError):
    """Raised on 5xx responses. ``status_code`` is the HTTP status code."""

    code = "SERVER_ERROR"

    def __init__(self, message: str = "Server error", status_code: int = 500) -> None:
        super().__init__(message)
        self.status_code = status_code


class NetworkError(MobiscrollConnectError):
    """Raised when the underlying HTTP transport fails (DNS, connection, timeout)."""

    code = "NETWORK_ERROR"

    def __init__(self, message: str = "Network error") -> None:
        super().__init__(message)
