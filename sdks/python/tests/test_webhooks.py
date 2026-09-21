import json

import httpx
import pytest
import respx

from mobiscroll_connect import (
    AuthenticationError,
    MobiscrollConnectClient,
    ServerError,
    TokenResponse,
    ValidationError,
)


@pytest.fixture
def client():
    c = MobiscrollConnectClient(
        client_id="cid", client_secret="csecret", redirect_uri="https://app/cb"
    )
    c.auth.set_credentials(TokenResponse(access_token="t"))
    yield c
    c.close()


@respx.mock
def test_subscribe_webhook_basic(client):
    route = respx.post("https://connect.mobiscroll.com/api/subscribe-webhook").mock(
        return_value=httpx.Response(200, json={
            "success": True,
            "provider": "google",
            "subscription": {
                "channelId": "chan-1",
                "resourceId": "res-1",
                "expiration": "2024-07-01T00:00:00Z",
            },
            "serverWebhookUrl": "https://connect.mobiscroll.com/api/webhooks/google",
            "channelId": "chan-1",
        })
    )
    response = client.webhooks.subscribe_webhook("google", "primary")

    assert response.success is True
    assert response.provider == "google"
    assert response.channel_id == "chan-1"
    assert response.server_webhook_url == "https://connect.mobiscroll.com/api/webhooks/google"
    assert response.subscription.channel_id == "chan-1"
    assert response.subscription.resource_id == "res-1"
    assert response.subscription.expiration == "2024-07-01T00:00:00Z"

    body = json.loads(route.calls.last.request.read())
    assert body == {"provider": "google", "calendarId": "primary"}


@respx.mock
def test_subscribe_webhook_passes_optional_fields(client):
    route = respx.post("https://connect.mobiscroll.com/api/subscribe-webhook").mock(
        return_value=httpx.Response(200, json={
            "success": True,
            "provider": "microsoft",
            "subscription": {"channelId": "chan-2"},
            "serverWebhookUrl": "https://connect.mobiscroll.com/api/webhooks/microsoft",
            "channelId": "chan-2",
        })
    )
    client.webhooks.subscribe_webhook(
        "microsoft", "cal-2", channel_id="chan-2", expiration=1735689600000
    )

    body = json.loads(route.calls.last.request.read())
    assert body == {
        "provider": "microsoft",
        "calendarId": "cal-2",
        "channelId": "chan-2",
        "expiration": 1735689600000,
    }


@respx.mock
def test_subscribe_webhook_validation_error(client):
    respx.post("https://connect.mobiscroll.com/api/subscribe-webhook").mock(
        return_value=httpx.Response(
            400, json={"message": "provider is required", "details": {"provider": ["required"]}}
        )
    )
    with pytest.raises(ValidationError) as exc_info:
        client.webhooks.subscribe_webhook("google", "primary")
    assert exc_info.value.details == {"provider": ["required"]}


@respx.mock
def test_subscribe_webhook_auth_error(client):
    respx.post("https://connect.mobiscroll.com/api/subscribe-webhook").mock(
        return_value=httpx.Response(401, json={"message": "bad token"})
    )
    with pytest.raises(AuthenticationError):
        client.webhooks.subscribe_webhook("google", "primary")


@respx.mock
def test_subscribe_webhook_server_error(client):
    respx.post("https://connect.mobiscroll.com/api/subscribe-webhook").mock(
        return_value=httpx.Response(500, json={"message": "boom"})
    )
    with pytest.raises(ServerError):
        client.webhooks.subscribe_webhook("google", "primary")


@respx.mock
def test_unsubscribe_webhook_basic(client):
    route = respx.post("https://connect.mobiscroll.com/api/unsubscribe-webhook").mock(
        return_value=httpx.Response(200, json={"success": True})
    )
    response = client.webhooks.unsubscribe_webhook("google", "chan-1", resource_id="res-1")

    assert response.success is True
    assert response.message is None
    body = json.loads(route.calls.last.request.read())
    assert body == {"provider": "google", "channelId": "chan-1", "resourceId": "res-1"}


@respx.mock
def test_unsubscribe_webhook_without_resource_id(client):
    route = respx.post("https://connect.mobiscroll.com/api/unsubscribe-webhook").mock(
        return_value=httpx.Response(200, json={"success": True})
    )
    client.webhooks.unsubscribe_webhook("caldav", "chan-3")

    body = json.loads(route.calls.last.request.read())
    assert body == {"provider": "caldav", "channelId": "chan-3"}


@respx.mock
def test_unsubscribe_webhook_success_with_message(client):
    """Provider-side unsubscribe failed (e.g. already expired), but the backend still
    reports success after removing the local mapping — callers should treat 200 as
    final regardless of the message."""
    respx.post("https://connect.mobiscroll.com/api/unsubscribe-webhook").mock(
        return_value=httpx.Response(200, json={
            "success": True,
            "message": "Subscription already expired upstream; local mapping removed.",
        })
    )
    response = client.webhooks.unsubscribe_webhook("google", "chan-1", resource_id="res-1")
    assert response.success is True
    assert "already expired" in response.message


@respx.mock
def test_unsubscribe_webhook_validation_error(client):
    respx.post("https://connect.mobiscroll.com/api/unsubscribe-webhook").mock(
        return_value=httpx.Response(400, json={"message": "channelId is required"})
    )
    with pytest.raises(ValidationError):
        client.webhooks.unsubscribe_webhook("google", "chan-1")


@respx.mock
def test_unsubscribe_webhook_auth_error(client):
    respx.post("https://connect.mobiscroll.com/api/unsubscribe-webhook").mock(
        return_value=httpx.Response(401, json={"message": "bad token"})
    )
    with pytest.raises(AuthenticationError):
        client.webhooks.unsubscribe_webhook("google", "chan-1")


@respx.mock
def test_unsubscribe_webhook_server_error(client):
    respx.post("https://connect.mobiscroll.com/api/unsubscribe-webhook").mock(
        return_value=httpx.Response(500, json={"message": "boom"})
    )
    with pytest.raises(ServerError):
        client.webhooks.unsubscribe_webhook("google", "chan-1")
