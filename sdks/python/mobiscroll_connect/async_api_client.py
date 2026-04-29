"""Async HTTP layer.

Behaviourally identical to :class:`ApiClient`, but uses :class:`httpx.AsyncClient`
and ``asyncio.Lock`` so concurrent 401s share a single in-flight refresh.
"""

from __future__ import annotations

import asyncio
import base64
from typing import Any, Awaitable, Callable, Mapping, Optional, Union
from urllib.parse import urlencode

import httpx

from ._internal.errors import map_response_error, map_transport_error
from .config import Config
from .exceptions import AuthenticationError, MobiscrollConnectError
from .models import TokenResponse

AsyncTokensRefreshedCallback = Callable[[TokenResponse], Union[None, Awaitable[None]]]


class AsyncApiClient:
    """Asynchronous transport. Use ``async with`` (or call ``aclose()``)
    so the underlying connection pool is released."""

    def __init__(
        self,
        config: Config,
        *,
        http_client: Optional[httpx.AsyncClient] = None,
        on_tokens_refreshed: Optional[AsyncTokensRefreshedCallback] = None,
    ) -> None:
        self._config = config
        self._credentials: Optional[TokenResponse] = None
        self._on_tokens_refreshed = on_tokens_refreshed
        self._refresh_lock = asyncio.Lock()
        self._owns_client = http_client is None

        self._http: httpx.AsyncClient = http_client or httpx.AsyncClient(
            base_url=config.base_url,
            timeout=config.timeout,
            headers={"Content-Type": "application/json"},
        )

    @property
    def config(self) -> Config:
        return self._config

    @property
    def base_url(self) -> str:
        return str(self._http.base_url).rstrip("/")

    def set_credentials(self, tokens: TokenResponse) -> None:
        self._credentials = tokens

    def get_credentials(self) -> Optional[TokenResponse]:
        return self._credentials

    def on_tokens_refreshed(self, callback: AsyncTokensRefreshedCallback) -> None:
        self._on_tokens_refreshed = callback

    async def aclose(self) -> None:
        if self._owns_client:
            await self._http.aclose()

    async def __aenter__(self) -> "AsyncApiClient":
        return self

    async def __aexit__(self, *exc_info: Any) -> None:
        await self.aclose()

    # ---- HTTP verbs -----------------------------------------------------

    async def get(self, path: str, params: Optional[Mapping[str, Any]] = None) -> Any:
        return await self._request_with_refresh("GET", path, params=params)

    async def post(
        self, path: str, json: Any = None, params: Optional[Mapping[str, Any]] = None
    ) -> Any:
        return await self._request_with_refresh("POST", path, json=json, params=params)

    async def put(self, path: str, json: Any = None) -> Any:
        return await self._request_with_refresh("PUT", path, json=json)

    async def delete(self, path: str, params: Optional[Mapping[str, Any]] = None) -> Any:
        return await self._request_with_refresh("DELETE", path, params=params)

    async def post_form(
        self, path: str, form: Mapping[str, Any], headers: Mapping[str, str]
    ) -> Any:
        try:
            response = await self._http.post(
                self._normalize_path(path),
                content=urlencode(dict(form)),
                headers={"Content-Type": "application/x-www-form-urlencoded", **headers},
            )
        except httpx.HTTPError as e:
            raise map_transport_error(e) from e
        if response.status_code >= 400:
            raise map_response_error(response)
        return self._parse_body(response)

    # ---- Internals ------------------------------------------------------

    async def _request_with_refresh(
        self,
        method: str,
        path: str,
        *,
        json: Any = None,
        params: Optional[Mapping[str, Any]] = None,
    ) -> Any:
        url = self._normalize_path(path)

        try:
            response = await self._http.request(
                method, url, json=json, params=params, headers=self._auth_headers()
            )
        except httpx.HTTPError as e:
            raise map_transport_error(e) from e

        if response.status_code == 401 and self._can_refresh():
            await self._refresh_access_token()
            try:
                response = await self._http.request(
                    method, url, json=json, params=params, headers=self._auth_headers()
                )
            except httpx.HTTPError as e:
                raise map_transport_error(e) from e

        if response.status_code >= 400:
            raise map_response_error(response)

        return self._parse_body(response)

    def _can_refresh(self) -> bool:
        return self._credentials is not None and bool(self._credentials.refresh_token)

    async def _refresh_access_token(self) -> None:
        async with self._refresh_lock:
            current = self._credentials
            if current is None or not current.refresh_token:
                raise AuthenticationError("No refresh token available")

            credentials = base64.b64encode(
                f"{self._config.client_id}:{self._config.client_secret}".encode()
            ).decode()

            try:
                response = await self._http.post(
                    "oauth/token",
                    content=urlencode(
                        {
                            "grant_type": "refresh_token",
                            "refresh_token": current.refresh_token,
                            "redirect_uri": self._config.redirect_uri,
                        }
                    ),
                    headers={
                        "Content-Type": "application/x-www-form-urlencoded",
                        "Authorization": f"Basic {credentials}",
                        "CLIENT_ID": self._config.client_id,
                    },
                )
            except httpx.HTTPError as e:
                raise AuthenticationError(f"Failed to refresh token: {e}") from e

            if response.status_code >= 400:
                raise AuthenticationError("Failed to refresh token")

            try:
                data = response.json() or {}
            except Exception as e:
                raise AuthenticationError(f"Failed to parse refresh response: {e}") from e

            new_tokens = TokenResponse.from_dict(data)
            merged = TokenResponse(
                access_token=new_tokens.access_token,
                token_type=new_tokens.token_type or current.token_type,
                expires_in=new_tokens.expires_in if new_tokens.expires_in is not None else current.expires_in,
                refresh_token=new_tokens.refresh_token or current.refresh_token,
            )
            self._credentials = merged

            if self._on_tokens_refreshed is not None:
                try:
                    result = self._on_tokens_refreshed(merged)
                    if asyncio.iscoroutine(result):
                        await result
                except Exception:
                    pass

    def _auth_headers(self) -> dict:
        if self._credentials and self._credentials.access_token:
            return {"Authorization": f"Bearer {self._credentials.access_token}"}
        return {}

    @staticmethod
    def _normalize_path(path: str) -> str:
        return path.lstrip("/")

    @staticmethod
    def _parse_body(response: httpx.Response) -> Any:
        if response.status_code == 204 or not response.content:
            return None
        try:
            return response.json()
        except Exception as e:
            raise MobiscrollConnectError(f"Invalid JSON response: {e}") from e
