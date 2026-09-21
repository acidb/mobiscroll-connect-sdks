from __future__ import annotations

from collections.abc import Mapping
from typing import Union

from ..api_client import ApiClient
from ..models import Provider, SubscribeWebhookResponse, UnsubscribeWebhookResponse

ProviderLike = Union[str, Provider]


class Webhooks:
    """Webhook subscriptions for calendar change notifications."""

    def __init__(self, api_client: ApiClient) -> None:
        self._api = api_client

    def subscribe_webhook(
        self,
        provider: ProviderLike,
        calendar_id: str,
        *,
        channel_id: str | None = None,
        expiration: int | None = None,
    ) -> SubscribeWebhookResponse:
        """Subscribe to change notifications for a calendar.

        :param provider: Calendar provider (``"google"``, ``"microsoft"``, ``"apple"``,
            or ``"caldav"``).
        :param calendar_id: Calendar to watch.
        :param channel_id: Client-supplied channel identifier. Auto-generated
            server-side when omitted.
        :param expiration: Provider-specific expiration timestamp (ms epoch).
        """
        payload: dict[str, object] = {
            "provider": str(provider.value if isinstance(provider, Provider) else provider),
            "calendarId": calendar_id,
        }
        if channel_id is not None:
            payload["channelId"] = channel_id
        if expiration is not None:
            payload["expiration"] = expiration

        data = self._api.post("subscribe-webhook", json=payload)
        return SubscribeWebhookResponse.from_dict(data if isinstance(data, Mapping) else {})

    def unsubscribe_webhook(
        self,
        provider: ProviderLike,
        channel_id: str,
        *,
        resource_id: str | None = None,
    ) -> UnsubscribeWebhookResponse:
        """Unsubscribe from webhook notifications for a channel.

        :param provider: Calendar provider the channel was subscribed with.
        :param channel_id: Channel identifier returned by ``subscribe_webhook``.
        :param resource_id: Required by some providers (e.g. Google) to fully
            unsubscribe.

        The server returns ``success=True`` even if the provider-side unsubscribe
        itself failed (e.g. an already-expired subscription) — it still removes the
        local mapping and explains why in ``message``. Treat a 200 response as final
        regardless of ``message``.
        """
        payload: dict[str, object] = {
            "provider": str(provider.value if isinstance(provider, Provider) else provider),
            "channelId": channel_id,
        }
        if resource_id is not None:
            payload["resourceId"] = resource_id

        data = self._api.post("unsubscribe-webhook", json=payload)
        return UnsubscribeWebhookResponse.from_dict(data if isinstance(data, Mapping) else {})
