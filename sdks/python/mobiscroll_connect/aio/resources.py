from __future__ import annotations

import base64
from typing import Any, AsyncIterator, Iterable, Mapping, Optional, Union
from urllib.parse import urlencode

from .._internal.payloads import (
    DateLike,
    build_delete_query,
    build_event_payload,
    build_list_events_query,
)
from ..async_api_client import AsyncApiClient
from ..exceptions import MobiscrollConnectError, ServerError
from ..models import (
    Calendar,
    CalendarEvent,
    ConnectionStatusResponse,
    DisconnectResponse,
    EventsListResponse,
    Provider,
    TokenResponse,
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
        state: Optional[str] = None,
        providers: Optional[str] = None,
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
        account: Optional[str] = None,
    ) -> DisconnectResponse:
        params = {"provider": str(provider.value if isinstance(provider, Provider) else provider)}
        if account:
            params["account"] = account
        try:
            data = await self._api.post("oauth/disconnect", json={}, params=params)
        except MobiscrollConnectError:
            data = await self._api.post("disconnect", json={}, params=params)
        return DisconnectResponse.from_dict(data or {})


class AsyncCalendars:
    def __init__(self, api_client: AsyncApiClient) -> None:
        self._api = api_client

    async def list(self) -> list:
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
        start: Optional[DateLike] = None,
        end: Optional[DateLike] = None,
        calendar_ids: Optional[Mapping[str, Iterable[str]]] = None,
        page_size: Optional[int] = None,
        next_page_token: Optional[str] = None,
        single_events: Optional[bool] = None,
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
        start: Optional[DateLike] = None,
        end: Optional[DateLike] = None,
        calendar_ids: Optional[Mapping[str, Iterable[str]]] = None,
        page_size: Optional[int] = None,
        single_events: Optional[bool] = None,
    ) -> AsyncIterator[CalendarEvent]:
        token: Optional[str] = None
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
        message = (
            response.get("message")
            if isinstance(response, Mapping) and isinstance(response.get("message"), str)
            else f"Failed to {operation} event"
        )
        raise ServerError(message, 400)
