"""Mobiscroll Connect SDK for Python.

Calendar and event management across Google Calendar, Microsoft Outlook,
Apple Calendar, and CalDAV through a single client.

Quick start::

    from mobiscroll_connect import MobiscrollConnectClient

    with MobiscrollConnectClient("client-id", "client-secret", "https://app/callback") as client:
        url = client.auth.generate_auth_url(user_id="user-123")
        # ...exchange code, set credentials...
        for event in client.events.iter_all(start="2024-01-01", end="2024-01-31"):
            print(event.title)

For asyncio, use :mod:`mobiscroll_connect.aio`::

    from mobiscroll_connect.aio import AsyncMobiscrollConnectClient
"""

from .client import MobiscrollConnectClient
from .config import Config
from .exceptions import (
    AuthenticationError,
    MobiscrollConnectError,
    NetworkError,
    NotFoundError,
    RateLimitError,
    ServerError,
    ValidationError,
)
from .models import (
    Calendar,
    CalendarEvent,
    ConnectedAccount,
    ConnectionStatusResponse,
    DisconnectResponse,
    EventAttendee,
    EventsListResponse,
    Provider,
    TokenResponse,
)

__version__ = "1.0.0"

__all__ = [
    "AuthenticationError",
    "Calendar",
    "CalendarEvent",
    "Config",
    "ConnectedAccount",
    "ConnectionStatusResponse",
    "DisconnectResponse",
    "EventAttendee",
    "EventsListResponse",
    "MobiscrollConnectClient",
    # Exceptions
    "MobiscrollConnectError",
    "NetworkError",
    "NotFoundError",
    # Models
    "Provider",
    "RateLimitError",
    "ServerError",
    "TokenResponse",
    "ValidationError",
    "__version__",
]
