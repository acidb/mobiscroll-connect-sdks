from __future__ import annotations

import base64
from collections.abc import AsyncIterator, Iterable, Mapping
from typing import Any, Union
from urllib.parse import urlencode

from .._internal.payloads import (
    DateLike,
    build_delete_query,
    build_event_payload,
    build_list_events_query,
)
from .._internal.webhook_keys import (
    WebhookKeyStore,
    is_retryable,
    keys_or_pinned,
    webhook_keys_url,
)
from ..async_api_client import AsyncApiClient
from ..exceptions import (
    MobiscrollConnectError,
    ServerError,
    ValidationError,
    WebhookVerificationError,
)
from ..models import (
    Calendar,
    CalendarEvent,
    ConnectionStatusResponse,
    DisconnectResponse,
    EventsListResponse,
    Provider,
    RevokeResponse,
    SubscribeWebhookResponse,
    TokenResponse,
    UnsubscribeWebhookResponse,
    WebhookDelivery,
)
from ..webhook_verification import (
    WebhookPayload,
    parse_webhook_delivery,
    verify_webhook_signature,
)

ProviderLike = Union[str, Provider]


class AsyncAuth:
    def __init__(self, api_client: AsyncApiClient) -> None:
        self._api = api_client

    def generate_auth_url(
        self,
        user_id: str,
        *,
        scope: str = "calendar",
        state: str | None = None,
        providers: str | None = None,
        lng: str | None = None,
    ) -> str:
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
        if lng is not None:
            params["lng"] = lng
        return f"{self._api.base_url}/oauth/authorize?{urlencode(params)}"

    async def get_token(self, code: str) -> TokenResponse:
        cfg = self._api.config
        credentials = base64.b64encode(
            f"{cfg.client_id}:{cfg.client_secret}".encode()
        ).decode()

        data = await self._api.post_form(
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

    async def get_connection_status(self) -> ConnectionStatusResponse:
        try:
            data = await self._api.get("oauth/connection-status")
        except MobiscrollConnectError:
            data = await self._api.get("connection-status")
        return ConnectionStatusResponse.from_dict(data or {})

    async def disconnect(
        self,
        provider: ProviderLike,
        *,
        account: str | None = None,
    ) -> DisconnectResponse:
        """Disconnect a calendar provider account.

        Removes the account (or, without ``account``, every account of the
        provider) from the user: stops its webhook subscriptions and deletes
        the stored provider credentials. It does not revoke the user's Connect
        access or refresh tokens (see :meth:`revoke`), nor the grant at Google
        or Microsoft.

        :param provider: The provider to disconnect.
        :param account: The ``id`` of an account from :meth:`get_connection_status`
            (``connections[provider][].id``). Omit to disconnect every account
            of the provider.

        Example::

            await client.auth.disconnect("google", account="user@gmail.com")
            await client.auth.disconnect(Provider.MICROSOFT)
        """
        params = {"provider": str(provider.value if isinstance(provider, Provider) else provider)}
        if account:
            params["account"] = account
        try:
            data = await self._api.post("oauth/disconnect", json={}, params=params)
        except MobiscrollConnectError:
            data = await self._api.post("disconnect", json={}, params=params)
        return DisconnectResponse.from_dict(data or {})

    async def revoke(self, token: str | None = None) -> RevokeResponse:
        """Revoke the user's Connect access and refresh tokens.

        Revokes the whole grant: every access and refresh token issued for this
        user. It does not disconnect calendar accounts or stop webhook
        subscriptions. To remove a user entirely, call :meth:`disconnect` for
        each provider first (it needs a valid access token), then ``revoke``.

        :param token: Access or refresh token to revoke. Defaults to the stored
            refresh token, else the stored access token; the stored credentials
            are then cleared. An explicit token leaves them untouched.
        :returns: ``success=True``, also for an unknown or already revoked token.
        :raises ValidationError: When no token is given and none is stored
            (no request is made).

        Example::

            await client.auth.disconnect("google")
            await client.auth.revoke()

            # Or revoke a token you stored yourself
            await client.auth.revoke(stored_refresh_token)
        """
        revoke_token = token
        if revoke_token is None:
            credentials = self._api.get_credentials()
            if credentials is not None:
                revoke_token = credentials.refresh_token or credentials.access_token
        if not revoke_token:
            raise ValidationError("No token to revoke: pass one or set credentials first")

        data = await self._api.post(
            "oauth/revoke", json={"token": revoke_token}, refresh_on_401=False
        )
        if token is None:
            self._api.clear_credentials()
        return RevokeResponse.from_dict(data or {})


class AsyncCalendars:
    def __init__(self, api_client: AsyncApiClient) -> None:
        self._api = api_client

    async def list(self) -> list[Calendar]:
        data = await self._api.get("calendars")
        if not isinstance(data, list):
            return []
        return [Calendar.from_dict(c) for c in data if isinstance(c, Mapping)]


class AsyncEvents:
    def __init__(self, api_client: AsyncApiClient) -> None:
        self._api = api_client

    async def list(
        self,
        *,
        start: DateLike | None = None,
        end: DateLike | None = None,
        calendar_ids: Mapping[str, Iterable[str]] | None = None,
        page_size: int | None = None,
        next_page_token: str | None = None,
        single_events: bool | None = None,
    ) -> EventsListResponse:
        query = build_list_events_query(
            start=start,
            end=end,
            calendar_ids=calendar_ids,
            page_size=page_size,
            next_page_token=next_page_token,
            single_events=single_events,
        )
        data = await self._api.get("events", params=query or None)
        return EventsListResponse.from_dict(data if isinstance(data, Mapping) else {})

    async def iter_all(
        self,
        *,
        start: DateLike | None = None,
        end: DateLike | None = None,
        calendar_ids: Mapping[str, Iterable[str]] | None = None,
        page_size: int | None = None,
        single_events: bool | None = None,
    ) -> AsyncIterator[CalendarEvent]:
        token: str | None = None
        while True:
            page = await self.list(
                start=start,
                end=end,
                calendar_ids=calendar_ids,
                page_size=page_size,
                next_page_token=token,
                single_events=single_events,
            )
            for event in page.events:
                yield event
            if not page.next_page_token:
                return
            token = page.next_page_token

    async def create(self, event: Mapping[str, Any]) -> CalendarEvent:
        payload = build_event_payload(event)
        response = await self._api.post("event", json=payload)
        return self._extract_event(response, "create")

    async def update(self, event: Mapping[str, Any]) -> CalendarEvent:
        payload = build_event_payload(event)
        response = await self._api.put("event", json=payload)
        return self._extract_event(response, "update")

    async def delete(self, params: Mapping[str, Any]) -> None:
        query = build_delete_query(params)
        response = await self._api.delete("event", params=query)
        if isinstance(response, Mapping) and response.get("success") is False:
            raise ServerError(str(response.get("message") or "Failed to delete event"), 400)

    @staticmethod
    def _extract_event(response: Any, operation: str) -> CalendarEvent:
        if isinstance(response, Mapping) and isinstance(response.get("event"), Mapping):
            return CalendarEvent.from_dict(response["event"])
        msg = response.get("message") if isinstance(response, Mapping) else None
        raise ServerError(msg if isinstance(msg, str) else f"Failed to {operation} event", 400)


class AsyncWebhooks:
    def __init__(self, api_client: AsyncApiClient) -> None:
        self._api = api_client

    async def subscribe_webhook(
        self,
        provider: ProviderLike,
        calendar_id: str,
        *,
        channel_id: str | None = None,
        expiration: int | None = None,
    ) -> SubscribeWebhookResponse:
        payload: dict[str, Any] = {
            "provider": str(provider.value if isinstance(provider, Provider) else provider),
            "calendarId": calendar_id,
        }
        if channel_id is not None:
            payload["channelId"] = channel_id
        if expiration is not None:
            payload["expiration"] = expiration

        data = await self._api.post("subscribe-webhook", json=payload)
        return SubscribeWebhookResponse.from_dict(data if isinstance(data, Mapping) else {})

    async def unsubscribe_webhook(
        self,
        provider: ProviderLike,
        channel_id: str,
        *,
        resource_id: str | None = None,
    ) -> UnsubscribeWebhookResponse:
        payload: dict[str, Any] = {
            "provider": str(provider.value if isinstance(provider, Provider) else provider),
            "channelId": channel_id,
        }
        if resource_id is not None:
            payload["resourceId"] = resource_id

        data = await self._api.post("unsubscribe-webhook", json=payload)
        return UnsubscribeWebhookResponse.from_dict(data if isinstance(data, Mapping) else {})

    async def verify_webhook(
        self, payload: WebhookPayload, headers: Mapping[str, Any]
    ) -> WebhookDelivery:
        """Async variant of :meth:`mobiscroll_connect.resources.Webhooks.verify_webhook`.

        Concurrent calls share one key fetch, and the key cache is shared with every
        other client in the process.
        """
        store = WebhookKeyStore.for_url(webhook_keys_url(self._api.base_url))
        pinned_key = self._api.config.webhook_public_key

        try:
            keys = await store.get_keys_async()
            verify_webhook_signature(payload, headers, keys_or_pinned(keys, pinned_key))
        except WebhookVerificationError as error:
            if not is_retryable(error) or not store.can_refetch():
                raise
            await store.refresh_async()
            keys = await store.get_keys_async()
            verify_webhook_signature(payload, headers, keys_or_pinned(keys, pinned_key))

        return parse_webhook_delivery(payload)
