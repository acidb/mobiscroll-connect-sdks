"""Exception hierarchy for the Mobiscroll Connect SDK.

All SDK errors inherit from :class:`MobiscrollConnectError` so callers can do a
broad ``except MobiscrollConnectError`` while keeping the option of catching
specific subclasses (e.g. :class:`AuthenticationError`).
"""

from __future__ import annotations

from typing import Any, Mapping, Optional


class MobiscrollConnectError(Exception):
    """Base class for all SDK errors."""

    code: str = "MOBISCROLL_ERROR"

    def __init__(self, message: str = "", code: Optional[str] = None) -> None:
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
        details: Optional[Mapping[str, Any]] = None,
    ) -> None:
        super().__init__(message)
        self.details: Mapping[str, Any] = details or {}


class RateLimitError(MobiscrollConnectError):
    """Raised on 429 responses. ``retry_after`` is the Retry-After header in seconds."""

    code = "RATE_LIMIT_ERROR"

    def __init__(
        self,
        message: str = "Rate limit exceeded",
        retry_after: Optional[int] = None,
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
