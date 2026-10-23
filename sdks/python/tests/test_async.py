import json

import httpx
import pytest
import respx

from mobiscroll_connect import AuthenticationError, TokenResponse, ValidationError
from mobiscroll_connect.aio import AsyncMobiscrollConnectClient


@pytest.fixture
async def async_client():
    c = AsyncMobiscrollConnectClient(
        client_id="cid", client_secret="csecret", redirect_uri="https://app/cb"
    )
    c.auth.set_credentials(TokenResponse(access_token="t", refresh_token="r"))
    yield c
    await c.aclose()


@respx.mock
async def test_async_list_calendars(async_client):
    respx.get("https://connect.mobiscroll.com/api/calendars").mock(
        return_value=httpx.Response(200, json=[
            {"provider": "google", "id": "primary", "title": "P", "timeZone": "UTC"},
        ])
    )
    calendars = await async_client.calendars.list()
    assert len(calendars) == 1
    assert calendars[0].id == "primary"


@respx.mock
async def test_async_list_events(async_client):
    respx.get("https://connect.mobiscroll.com/api/events").mock(
        return_value=httpx.Response(200, json={
            "events": [{"provider": "google", "id": "1", "calendarId": "primary"}],
        })
    )
    r = await async_client.events.list()
    assert len(r) == 1


@respx.mock
async def test_async_iter_all_paginates(async_client):
    respx.get("https://connect.mobiscroll.com/api/events").mock(
        side_effect=[
            httpx.Response(200, json={
                "events": [{"provider": "google", "id": "1", "calendarId": "p"}],
                "nextPageToken": "p2",
            }),
            httpx.Response(200, json={
                "events": [{"provider": "google", "id": "2", "calendarId": "p"}],
            }),
        ]
    )
    ids = []
    async for e in async_client.events.iter_all():
        ids.append(e.id)
    assert ids == ["1", "2"]


@respx.mock
async def test_async_token_refresh(async_client):
    respx.get("https://connect.mobiscroll.com/api/events").mock(
        side_effect=[
            httpx.Response(401, json={}),
            httpx.Response(200, json={"events": []}),
        ]
    )
    refresh_route = respx.post("https://connect.mobiscroll.com/api/oauth/token").mock(
        return_value=httpx.Response(200, json={"access_token": "new", "refresh_token": "r2"})
    )
    captured = []
    async_client.on_tokens_refreshed(lambda t: captured.append(t))

    r = await async_client.events.list()
    assert r.events == []
    assert refresh_route.called
    assert captured[0].access_token == "new"


@respx.mock
async def test_async_subscribe_webhook(async_client):
    route = respx.post("https://connect.mobiscroll.com/api/subscribe-webhook").mock(
        return_value=httpx.Response(200, json={
            "success": True,
            "provider": "google",
            "subscription": {"channelId": "chan-1", "resourceId": "res-1"},
            "serverWebhookUrl": "https://connect.mobiscroll.com/api/webhooks/google",
            "channelId": "chan-1",
        })
    )
    response = await async_client.webhooks.subscribe_webhook("google", "primary")
    assert response.success is True
    assert response.subscription.resource_id == "res-1"
    assert route.called


@respx.mock
async def test_async_unsubscribe_webhook(async_client):
    route = respx.post("https://connect.mobiscroll.com/api/unsubscribe-webhook").mock(
        return_value=httpx.Response(200, json={"success": True})
    )
    response = await async_client.webhooks.unsubscribe_webhook(
        "google", "chan-1", resource_id="res-1"
    )
    assert response.success is True
    assert route.called


async def test_async_context_manager_closes():
    async with AsyncMobiscrollConnectClient(
        client_id="cid", client_secret="csecret", redirect_uri="https://app/cb"
    ) as client:
        url = client.auth.generate_auth_url(user_id="u")
        assert "oauth/authorize" in url


REVOKE_URL = "https://connect.mobiscroll.com/api/oauth/revoke"


@respx.mock
async def test_async_revoke_explicit_token_keeps_credentials(async_client):
    route = respx.post(REVOKE_URL).mock(return_value=httpx.Response(200, json={"success": True}))
    result = await async_client.auth.revoke("explicit-token")
    assert result.success is True
    assert json.loads(route.calls.last.request.content) == {"token": "explicit-token"}
    assert async_client._api.get_credentials() == TokenResponse(access_token="t", refresh_token="r")


@respx.mock
async def test_async_revoke_defaults_to_stored_refresh_token_and_clears(async_client):
    route = respx.post(REVOKE_URL).mock(return_value=httpx.Response(200, json={"success": True}))
    await async_client.auth.revoke()
    assert json.loads(route.calls.last.request.content) == {"token": "r"}
    assert async_client._api.get_credentials() is None


@respx.mock
async def test_async_revoke_falls_back_to_stored_access_token(async_client):
    async_client.auth.set_credentials(TokenResponse(access_token="only-access"))
    route = respx.post(REVOKE_URL).mock(return_value=httpx.Response(200, json={"success": True}))
    await async_client.auth.revoke()
    assert json.loads(route.calls.last.request.content) == {"token": "only-access"}
    assert async_client._api.get_credentials() is None


@respx.mock
async def test_async_revoke_without_token_raises_without_request():
    async with AsyncMobiscrollConnectClient(
        client_id="cid", client_secret="csecret", redirect_uri="https://app/cb"
    ) as c:
        with pytest.raises(ValidationError):
            await c.auth.revoke()
    assert respx.calls.call_count == 0


@respx.mock
async def test_async_revoke_never_refreshes_on_401(async_client):
    respx.post(REVOKE_URL).mock(return_value=httpx.Response(401, json={}))
    refresh_route = respx.post("https://connect.mobiscroll.com/api/oauth/token").mock(
        return_value=httpx.Response(200, json={"access_token": "new"})
    )
    with pytest.raises(AuthenticationError):
        await async_client.auth.revoke()
    assert not refresh_route.called
    assert async_client._api.get_credentials() is not None


@respx.mock
async def test_async_create_and_update_return_the_bare_event(async_client):
    event = {
        "provider": "google", "id": "evt-1", "calendarId": "primary", "title": "x",
        "start": "2024-06-15T10:00:00.000Z", "end": "2024-06-15T11:00:00.000Z",
    }
    respx.post("https://connect.mobiscroll.com/api/event").mock(return_value=httpx.Response(201, json=event))
    respx.put("https://connect.mobiscroll.com/api/event").mock(
        return_value=httpx.Response(200, json={**event, "title": "y"})
    )
    created = await async_client.events.create({
        "provider": "google", "calendar_id": "primary", "title": "x",
        "start": "2024-06-15T10:00:00Z", "end": "2024-06-15T11:00:00Z",
    })
    updated = await async_client.events.update({
        "provider": "google", "calendar_id": "primary", "event_id": "evt-1", "title": "y",
    })
    assert created.id == "evt-1"
    assert updated.title == "y"
