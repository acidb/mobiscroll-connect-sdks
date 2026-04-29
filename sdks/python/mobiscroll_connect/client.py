"""Public synchronous client. The thing most users import."""

from __future__ import annotations

from typing import Any, Optional

import httpx

from .api_client import ApiClient, TokensRefreshedCallback
from .config import Config
from .models import TokenResponse
from .resources import Auth, Calendars, Events


class MobiscrollConnectClient:
    """Synchronous Mobiscroll Connect client.

    Resources are exposed as attributes (``client.auth``, ``client.calendars``,
    ``client.events``) — call them directly without parentheses, e.g.::

        client.calendars.list()
        client.events.create({...})

    Use as a context manager to ensure the underlying HTTP connection pool is
    released::

        with MobiscrollConnectClient(...) as client:
            calendars = client.calendars.list()
    """

    def __init__(
        self,
        client_id: str,
        client_secret: str,
        redirect_uri: str,
        *,
        base_url: Optional[str] = None,
        timeout: Optional[float] = None,
        http_client: Optional[httpx.Client] = None,
        on_tokens_refreshed: Optional[TokensRefreshedCallback] = None,
    ) -> None:
        config_kwargs = {"client_id": client_id, "client_secret": client_secret, "redirect_uri": redirect_uri}
        if base_url is not None:
            config_kwargs["base_url"] = base_url
        if timeout is not None:
            config_kwargs["timeout"] = timeout

        self._config = Config(**config_kwargs)
        self._api = ApiClient(
            self._config,
            http_client=http_client,
            on_tokens_refreshed=on_tokens_refreshed,
        )
        self.auth = Auth(self._api)
        self.calendars = Calendars(self._api)
        self.events = Events(self._api)

    @property
    def config(self) -> Config:
        return self._config

    def set_credentials(self, tokens: TokenResponse) -> None:
        """Shortcut for ``client.auth.set_credentials(tokens)``."""
        self.auth.set_credentials(tokens)

    def on_tokens_refreshed(self, callback: TokensRefreshedCallback) -> None:
        """Register a callback invoked after every automatic token refresh.
        Use this to persist the new tokens (DB, session store, etc.)."""
        self._api.on_tokens_refreshed(callback)

    def close(self) -> None:
        self._api.close()

    def __enter__(self) -> "MobiscrollConnectClient":
        return self

    def __exit__(self, *exc_info: Any) -> None:
        self.close()
