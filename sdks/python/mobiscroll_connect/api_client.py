"""Synchronous HTTP layer.

Wraps an :class:`httpx.Client` with:

* ``Authorization: Bearer`` header injection
* automatic 401 → token refresh → single retry
* HTTP error → typed SDK exception mapping
* an ``on_tokens_refreshed`` callback so callers can persist new tokens

Token refresh is serialized through a ``threading.Lock``: parallel 401s wait on
the same in-flight refresh instead of racing.
"""

from __future__ import annotations

import base64
import threading
from typing import Any, Callable, Mapping, Optional
from urllib.parse import urlencode

import httpx

from ._internal.errors import map_response_error, map_transport_error
from .config import Config
from .exceptions import AuthenticationError, MobiscrollConnectError
from .models import TokenResponse

TokensRefreshedCallback = Callable[[TokenResponse], None]


class ApiClient:
    """Synchronous transport. Not normally constructed directly — use
    :class:`mobiscroll_connect.MobiscrollConnectClient`."""

    def __init__(
        self,
        config: Config,
        *,
        http_client: Optional[httpx.Client] = None,
        on_tokens_refreshed: Optional[TokensRefreshedCallback] = None,
    ) -> None:
        self._config = config
        self._credentials: Optional[TokenResponse] = None
        self._on_tokens_refreshed = on_tokens_refreshed
        self._refresh_lock = threading.Lock()
        self._owns_client = http_client is None

        self._http: httpx.Client = http_client or httpx.Client(
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

    def on_tokens_refreshed(self, callback: TokensRefreshedCallback) -> None:
        """Register a callback to persist tokens after an automatic refresh."""
        self._on_tokens_refreshed = callback

    def close(self) -> None:
        if self._owns_client:
            self._http.close()

    def __enter__(self) -> "ApiClient":
        return self

    def __exit__(self, *exc_info: Any) -> None:
        self.close()

    # ---- HTTP verbs -----------------------------------------------------

    def get(self, path: str, params: Optional[Mapping[str, Any]] = None) -> Any:
        return self._request_with_refresh("GET", path, params=params)

    def post(self, path: str, json: Any = None, params: Optional[Mapping[str, Any]] = None) -> Any:
        return self._request_with_refresh("POST", path, json=json, params=params)

    def put(self, path: str, json: Any = None) -> Any:
        return self._request_with_refresh("PUT", path, json=json)

    def delete(self, path: str, params: Optional[Mapping[str, Any]] = None) -> Any:
        return self._request_with_refresh("DELETE", path, params=params)

    def post_form(self, path: str, form: Mapping[str, Any], headers: Mapping[str, str]) -> Any:
        """Form-encoded POST without auth retry — used for the OAuth token endpoints
        themselves, where 401 is terminal (we cannot refresh by definition)."""
        try:
            response = self._http.post(
                self._normalize_path(path),
                content=urlencode(dict(form)),
                headers={**self._json_headers(content_type="application/x-www-form-urlencoded"), **headers},
            )
        except httpx.HTTPError as e:
            raise map_transport_error(e) from e
        if response.status_code >= 400:
            raise map_response_error(response)
        return self._parse_body(response)

    # ---- Internals ------------------------------------------------------

    def _request_with_refresh(
        self,
        method: str,
        path: str,
        *,
        json: Any = None,
        params: Optional[Mapping[str, Any]] = None,
    ) -> Any:
        url = self._normalize_path(path)

        try:
            response = self._http.request(
                method, url, json=json, params=params, headers=self._auth_headers()
            )
        except httpx.HTTPError as e:
            raise map_transport_error(e) from e

        if response.status_code == 401 and self._can_refresh():
            self._refresh_access_token()
            try:
                response = self._http.request(
                    method, url, json=json, params=params, headers=self._auth_headers()
                )
            except httpx.HTTPError as e:
                raise map_transport_error(e) from e

        if response.status_code >= 400:
            raise map_response_error(response)

        return self._parse_body(response)

    def _can_refresh(self) -> bool:
        return self._credentials is not None and bool(self._credentials.refresh_token)

    def _refresh_access_token(self) -> None:
        with self._refresh_lock:
            # Another thread may have refreshed while we waited; re-check.
            current = self._credentials
            if current is None or not current.refresh_token:
                raise AuthenticationError("No refresh token available")

            credentials = base64.b64encode(
                f"{self._config.client_id}:{self._config.client_secret}".encode()
            ).decode()

            try:
                response = self._http.post(
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
                # Surface as AuthenticationError; the original error type is irrelevant
                # to callers — they need to re-authorize regardless.
                raise AuthenticationError("Failed to refresh token")

            try:
                data = response.json() or {}
            except Exception as e:
                raise AuthenticationError(f"Failed to parse refresh response: {e}") from e

            new_tokens = TokenResponse.from_dict(data)
            # Preserve the existing refresh token if the server did not issue a new one.
            merged = TokenResponse(
                access_token=new_tokens.access_token,
                token_type=new_tokens.token_type or current.token_type,
                expires_in=new_tokens.expires_in if new_tokens.expires_in is not None else current.expires_in,
                refresh_token=new_tokens.refresh_token or current.refresh_token,
            )
            self._credentials = merged

            if self._on_tokens_refreshed is not None:
                try:
                    self._on_tokens_refreshed(merged)
                except Exception:
                    # Persistence callbacks must not break the refresh path.
                    pass

    def _auth_headers(self) -> dict:
        if self._credentials and self._credentials.access_token:
            return {"Authorization": f"Bearer {self._credentials.access_token}"}
        return {}

    @staticmethod
    def _json_headers(content_type: str = "application/json") -> dict:
        return {"Content-Type": content_type}

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
