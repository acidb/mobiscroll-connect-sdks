from __future__ import annotations

import base64
from typing import Optional, Union
from urllib.parse import urlencode

from ..api_client import ApiClient
from ..exceptions import MobiscrollConnectError
from ..models import (
    ConnectionStatusResponse,
    DisconnectResponse,
    Provider,
    TokenResponse,
)

ProviderLike = Union[str, Provider]


class Auth:
    """OAuth2 flow + connection management."""

    def __init__(self, api_client: ApiClient) -> None:
        self._api = api_client

    def generate_auth_url(
        self,
        user_id: str,
        *,
        scope: str = "calendar",
        state: Optional[str] = None,
        providers: Optional[str] = None,
    ) -> str:
        """Build the OAuth2 authorization URL to redirect the user to.

        :param user_id: External app user identifier (required).
        :param scope: OAuth scope (default ``"calendar"``).
        :param state: Opaque CSRF protection value.
        :param providers: Comma-separated list (e.g. ``"google,microsoft"``)
            to restrict the provider picker.
        """
        cfg = self._api.config
        params = {
            "client_id": cfg.client_id,
            "response_type": "code",
            "user_id": user_id,
            "redirect_uri": cfg.redirect_uri,
            "scope": scope,
        }
        if state is not None:
            params["state"] = state
        if providers is not None:
            params["providers"] = providers

        return f"{self._api.base_url}/oauth/authorize?{urlencode(params)}"

    def get_token(self, code: str) -> TokenResponse:
        """Exchange an authorization code for tokens. Sets credentials on the
        client automatically — you do not need to call ``set_credentials``
        immediately afterwards."""
        cfg = self._api.config
        credentials = base64.b64encode(
            f"{cfg.client_id}:{cfg.client_secret}".encode()
        ).decode()

        data = self._api.post_form(
            "oauth/token",
            form={
                "grant_type": "authorization_code",
                "code": code,
                "redirect_uri": cfg.redirect_uri,
            },
            headers={
                "Authorization": f"Basic {credentials}",
                "CLIENT_ID": cfg.client_id,
            },
        )
        tokens = TokenResponse.from_dict(data or {})
        self._api.set_credentials(tokens)
        return tokens

    def set_credentials(self, tokens: TokenResponse) -> None:
        self._api.set_credentials(tokens)

    def get_connection_status(self) -> ConnectionStatusResponse:
        """Connected providers and account counts. Falls back to the legacy
        ``/connection-status`` route on older deployments."""
        try:
            data = self._api.get("oauth/connection-status")
        except MobiscrollConnectError:
            data = self._api.get("connection-status")
        return ConnectionStatusResponse.from_dict(data or {})

    def disconnect(
        self,
        provider: ProviderLike,
        *,
        account: Optional[str] = None,
    ) -> DisconnectResponse:
        """Revoke a provider connection. Pass ``account`` to disconnect a
        single account; omit it to disconnect every account for that provider."""
        params = {"provider": str(provider.value if isinstance(provider, Provider) else provider)}
        if account:
            params["account"] = account

        try:
            data = self._api.post("oauth/disconnect", json={}, params=params)
        except MobiscrollConnectError:
            data = self._api.post("disconnect", json={}, params=params)
        return DisconnectResponse.from_dict(data or {})
