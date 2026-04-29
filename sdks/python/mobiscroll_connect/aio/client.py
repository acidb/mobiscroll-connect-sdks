"""Public async client."""

from __future__ import annotations

from typing import Any, Optional

import httpx

from ..async_api_client import AsyncApiClient, AsyncTokensRefreshedCallback
from ..config import Config
from ..models import TokenResponse
from .resources import AsyncAuth, AsyncCalendars, AsyncEvents


class AsyncMobiscrollConnectClient:
    """Async variant of :class:`mobiscroll_connect.MobiscrollConnectClient`.

    Use as an async context manager to release the connection pool::

        async with AsyncMobiscrollConnectClient(...) as client:
            calendars = await client.calendars.list()
    """

    def __init__(
        self,
        client_id: str,
        client_secret: str,
        redirect_uri: str,
        *,
        base_url: Optional[str] = None,
        timeout: Optional[float] = None,
        http_client: Optional[httpx.AsyncClient] = None,
        on_tokens_refreshed: Optional[AsyncTokensRefreshedCallback] = None,
    ) -> None:
        config_kwargs = {"client_id": client_id, "client_secret": client_secret, "redirect_uri": redirect_uri}
        if base_url is not None:
            config_kwargs["base_url"] = base_url
        if timeout is not None:
            config_kwargs["timeout"] = timeout

        self._config = Config(**config_kwargs)
        self._api = AsyncApiClient(
            self._config,
            http_client=http_client,
            on_tokens_refreshed=on_tokens_refreshed,
        )
        self.auth = AsyncAuth(self._api)
        self.calendars = AsyncCalendars(self._api)
        self.events = AsyncEvents(self._api)

    @property
    def config(self) -> Config:
        return self._config

    def set_credentials(self, tokens: TokenResponse) -> None:
        """Shortcut for ``client.auth.set_credentials(tokens)``."""
        self.auth.set_credentials(tokens)

    def on_tokens_refreshed(self, callback: AsyncTokensRefreshedCallback) -> None:
        self._api.on_tokens_refreshed(callback)

    async def aclose(self) -> None:
        await self._api.aclose()

    async def __aenter__(self) -> "AsyncMobiscrollConnectClient":
        return self

    async def __aexit__(self, *exc_info: Any) -> None:
        await self.aclose()
