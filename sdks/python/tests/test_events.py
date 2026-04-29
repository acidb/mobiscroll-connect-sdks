from urllib.parse import parse_qs, urlparse

import httpx
import pytest
import respx

from mobiscroll_connect import (
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
    c.auth.set_credentials(TokenResponse(access_token="t", refresh_token="r"))
    yield c
    c.close()


@respx.mock
def test_events_list_basic(client):
    respx.get("https://connect.mobiscroll.com/api/events").mock(
        return_value=httpx.Response(200, json={
            "events": [
                {"provider": "google", "id": "1", "calendarId": "primary", "title": "A"}
            ],
            "nextPageToken": "page-2",
        })
    )
    r = client.events.list()
    assert len(r) == 1
    assert r.events[0].title == "A"
    assert r.has_more is True


@respx.mock
def test_events_list_passes_query(client):
    route = respx.get("https://connect.mobiscroll.com/api/events").mock(
        return_value=httpx.Response(200, json={"events": []})
    )
    client.events.list(
        start="2024-01-01T00:00:00Z",
        end="2024-01-31T00:00:00Z",
        calendar_ids={"google": ["primary"]},
        page_size=50,
        single_events=True,
    )
    params = parse_qs(urlparse(str(route.calls.last.request.url)).query)
    assert params["start"] == ["2024-01-01T00:00:00Z"]
    assert params["pageSize"] == ["50"]
    assert params["singleEvents"] == ["true"]


@respx.mock
def test_events_list_validation_error(client):
    respx.get("https://connect.mobiscroll.com/api/events").mock(
        return_value=httpx.Response(400, json={"message": "bad", "details": {"start": ["x"]}})
    )
    with pytest.raises(ValidationError) as exc_info:
        client.events.list()
    assert exc_info.value.details == {"start": ["x"]}


@respx.mock
def test_iter_all_follows_pagination(client):
    respx.get("https://connect.mobiscroll.com/api/events").mock(
        side_effect=[
            httpx.Response(200, json={
                "events": [{"provider": "google", "id": "1", "calendarId": "primary"}],
                "nextPageToken": "p2",
            }),
            httpx.Response(200, json={
                "events": [{"provider": "google", "id": "2", "calendarId": "primary"}],
            }),
        ]
    )
    ids = [e.id for e in client.events.iter_all()]
    assert ids == ["1", "2"]


@respx.mock
def test_create_event(client):
    route = respx.post("https://connect.mobiscroll.com/api/event").mock(
        return_value=httpx.Response(200, json={
            "event": {
                "provider": "google", "id": "evt-1", "calendarId": "primary",
                "title": "x", "start": "2024-06-15T10:00:00Z", "end": "2024-06-15T11:00:00Z",
            }
        })
    )
    e = client.events.create({
        "provider": "google",
        "calendar_id": "primary",
        "title": "x",
        "start": "2024-06-15T10:00:00Z",
        "end": "2024-06-15T11:00:00Z",
    })
    assert e.id == "evt-1"
    body = route.calls.last.request.read().decode()
    assert "calendarId" in body
    assert "calendar_id" not in body


@respx.mock
def test_create_missing_event_raises(client):
    respx.post("https://connect.mobiscroll.com/api/event").mock(
        return_value=httpx.Response(200, json={"message": "boom"})
    )
    with pytest.raises(ServerError):
        client.events.create({
            "provider": "google", "calendar_id": "primary", "title": "x",
            "start": "2024-06-15T10:00:00Z", "end": "2024-06-15T11:00:00Z",
        })


@respx.mock
def test_delete_event(client):
    route = respx.delete("https://connect.mobiscroll.com/api/event").mock(
        return_value=httpx.Response(200, json={"success": True})
    )
    client.events.delete({
        "provider": "google", "calendar_id": "primary", "event_id": "evt-1",
    })
    params = parse_qs(urlparse(str(route.calls.last.request.url)).query)
    assert params["calendarId"] == ["primary"]
    assert params["eventId"] == ["evt-1"]


@respx.mock
def test_delete_failure_raises(client):
    respx.delete("https://connect.mobiscroll.com/api/event").mock(
        return_value=httpx.Response(200, json={"success": False, "message": "nope"})
    )
    with pytest.raises(ServerError, match="nope"):
        client.events.delete({
            "provider": "google", "calendar_id": "primary", "event_id": "evt-1",
        })


def test_delete_validates_required():
    c = MobiscrollConnectClient("cid", "cs", "https://app/cb")
    c.auth.set_credentials(TokenResponse(access_token="t"))
    with pytest.raises(ValueError, match="event_id"):
        c.events.delete({"provider": "google", "calendar_id": "primary"})
    c.close()
