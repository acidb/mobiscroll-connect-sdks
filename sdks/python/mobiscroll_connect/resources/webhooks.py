from __future__ import annotations

from collections.abc import Mapping
from typing import Any, Union

from .._internal.webhook_keys import (
    WebhookKeyStore,
    is_retryable,
    keys_or_pinned,
    webhook_keys_url,
)
from ..api_client import ApiClient
from ..exceptions import WebhookVerificationError
from ..models import (
    Provider,
    SubscribeWebhookResponse,
    UnsubscribeWebhookResponse,
    WebhookDelivery,
)
from ..webhook_verification import (
    WebhookPayload,
    parse_webhook_delivery,
    verify_webhook_signature,
)

ProviderLike = Union[str, Provider]


class Webhooks:
    """Webhook subscriptions for calendar change notifications, and delivery verification."""

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

    def verify_webhook(
        self, payload: WebhookPayload, headers: Mapping[str, Any]
    ) -> WebhookDelivery:
        """Verify that a webhook delivery came from Mobiscroll Connect, and parse it.

        Fetches the public keys from ``/.well-known/webhook-keys`` on first use and
        caches them for the whole process, refreshing them as the endpoint's
        ``Cache-Control`` allows. When no signature matches, it re-fetches the keys once
        (at most once a minute) before rejecting, so a key rotation never rejects genuine
        deliveries. ``webhook_public_key`` from the config is used only when the endpoint
        cannot be reached.

        :param payload: The raw request body, exactly as received (``bytes`` or ``str``);
            not a parsed object.
        :param headers: The request headers — any mapping, e.g. Flask's
            ``request.headers``; lookup is case-insensitive.
        :raises WebhookVerificationError: When the delivery is not genuine; respond
            with a 4xx.

        Example::

            @app.post("/webhooks/mobiscroll")
            def mobiscroll_webhook():
                try:
                    delivery = client.webhooks.verify_webhook(request.get_data(), request.headers)
                except WebhookVerificationError:
                    return "", 401
                handle_delivery(delivery)
                return "", 204
        """
        store = WebhookKeyStore.for_url(webhook_keys_url(self._api.base_url))
        pinned_key = self._api.config.webhook_public_key

        try:
            keys = store.get_keys()
            verify_webhook_signature(payload, headers, keys_or_pinned(keys, pinned_key))
        except WebhookVerificationError as error:
            if not is_retryable(error) or not store.can_refetch():
                raise
            store.refresh()
            keys = store.get_keys()
            verify_webhook_signature(payload, headers, keys_or_pinned(keys, pinned_key))

        return parse_webhook_delivery(payload)
