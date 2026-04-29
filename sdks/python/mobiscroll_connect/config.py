from __future__ import annotations

from dataclasses import dataclass

DEFAULT_BASE_URL = "https://connect.mobiscroll.com/api"
DEFAULT_TIMEOUT = 30.0


@dataclass(frozen=True)
class Config:
    """Immutable client configuration."""

    client_id: str
    client_secret: str
    redirect_uri: str
    base_url: str = DEFAULT_BASE_URL
    timeout: float = DEFAULT_TIMEOUT

    def __post_init__(self) -> None:
        if not self.client_id or not self.client_secret or not self.redirect_uri:
            from .exceptions import MobiscrollConnectError

            raise MobiscrollConnectError(
                "client_id, client_secret and redirect_uri are required"
            )
