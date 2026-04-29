import httpx
import pytest
import respx

from mobiscroll_connect import TokenResponse
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


async def test_async_context_manager_closes():
    async with AsyncMobiscrollConnectClient(
        client_id="cid", client_secret="csecret", redirect_uri="https://app/cb"
    ) as client:
        url = client.auth.generate_auth_url(user_id="u")
        assert "oauth/authorize" in url
