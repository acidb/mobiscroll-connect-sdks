from urllib.parse import parse_qs, urlparse

import httpx
import pytest
import respx

from mobiscroll_connect import (
    AuthenticationError,
    MobiscrollConnectClient,
    TokenResponse,
)


@pytest.fixture
def client():
    c = MobiscrollConnectClient(
        client_id="cid",
        client_secret="csecret",
        redirect_uri="https://app/callback",
    )
    yield c
    c.close()


def test_generate_auth_url_minimal(client):
    url = client.auth.generate_auth_url(user_id="user-1")
    parsed = urlparse(url)
    params = parse_qs(parsed.query)
    assert parsed.path.endswith("/oauth/authorize")
    assert params["client_id"] == ["cid"]
    assert params["response_type"] == ["code"]
    assert params["user_id"] == ["user-1"]
    assert params["redirect_uri"] == ["https://app/callback"]
    assert params["scope"] == ["calendar"]


def test_generate_auth_url_optional(client):
    url = client.auth.generate_auth_url(
        user_id="u", state="csrf", providers="google,microsoft", scope="read-write"
    )
    params = parse_qs(urlparse(url).query)
    assert params["state"] == ["csrf"]
    assert params["providers"] == ["google,microsoft"]
    assert params["scope"] == ["read-write"]


@respx.mock
def test_get_token_sets_credentials(client):
    route = respx.post("https://connect.mobiscroll.com/api/oauth/token").mock(
        return_value=httpx.Response(200, json={
            "access_token": "new-token",
            "token_type": "Bearer",
            "expires_in": 3600,
            "refresh_token": "new-refresh",
        })
    )
    tokens = client.auth.get_token("auth-code")
    assert tokens.access_token == "new-token"
    assert client._api.get_credentials().access_token == "new-token"
    assert route.called
    body = route.calls.last.request.content.decode()
    assert "grant_type=authorization_code" in body
    assert "code=auth-code" in body
    auth_header = route.calls.last.request.headers["authorization"]
    assert auth_header.startswith("Basic ")


@respx.mock
def test_connection_status_oauth_path(client):
    client.auth.set_credentials(TokenResponse(access_token="t"))
    respx.get("https://connect.mobiscroll.com/api/oauth/connection-status").mock(
        return_value=httpx.Response(200, json={
            "connections": {"google": [{"id": "u@gmail.com"}]},
            "limitReached": False,
        })
    )
    status = client.auth.get_connection_status()
    assert status.connections["google"][0].id == "u@gmail.com"


@respx.mock
def test_connection_status_legacy_fallback(client):
    client.auth.set_credentials(TokenResponse(access_token="t"))
    respx.get("https://connect.mobiscroll.com/api/oauth/connection-status").mock(
        return_value=httpx.Response(404, json={})
    )
    respx.get("https://connect.mobiscroll.com/api/connection-status").mock(
        return_value=httpx.Response(200, json={"connections": {}, "limitReached": False})
    )
    status = client.auth.get_connection_status()
    assert status.connections == {}


@respx.mock
def test_disconnect_sends_query(client):
    client.auth.set_credentials(TokenResponse(access_token="t"))
    route = respx.post("https://connect.mobiscroll.com/api/oauth/disconnect").mock(
        return_value=httpx.Response(200, json={"success": True})
    )
    result = client.auth.disconnect("google", account="u@gmail.com")
    assert result.success is True
    params = parse_qs(urlparse(str(route.calls.last.request.url)).query)
    assert params["provider"] == ["google"]
    assert params["account"] == ["u@gmail.com"]


@respx.mock
def test_token_refresh_on_401_retries(client):
    client.auth.set_credentials(TokenResponse(
        access_token="old", refresh_token="r1", expires_in=1
    ))
    events_route = respx.get("https://connect.mobiscroll.com/api/events").mock(
        side_effect=[
            httpx.Response(401, json={"message": "expired"}),
            httpx.Response(200, json={"events": []}),
        ]
    )
    refresh_route = respx.post("https://connect.mobiscroll.com/api/oauth/token").mock(
        return_value=httpx.Response(200, json={
            "access_token": "new", "refresh_token": "r2", "expires_in": 3600
        })
    )
    result = client.events.list()
    assert events_route.call_count == 2
    assert refresh_route.called
    assert client._api.get_credentials().access_token == "new"
    assert result.events == []


@respx.mock
def test_token_refresh_callback_invoked(client):
    client.auth.set_credentials(TokenResponse(access_token="old", refresh_token="r1"))
    captured = []
    client.on_tokens_refreshed(captured.append)

    respx.get("https://connect.mobiscroll.com/api/events").mock(
        side_effect=[
            httpx.Response(401, json={}),
            httpx.Response(200, json={"events": []}),
        ]
    )
    respx.post("https://connect.mobiscroll.com/api/oauth/token").mock(
        return_value=httpx.Response(200, json={"access_token": "new"})
    )

    client.events.list()
    assert len(captured) == 1
    assert captured[0].access_token == "new"
    # Refresh token preserved when server doesn't return a new one
    assert captured[0].refresh_token == "r1"


@respx.mock
def test_token_refresh_failure_raises_auth_error(client):
    client.auth.set_credentials(TokenResponse(access_token="old", refresh_token="r1"))
    respx.get("https://connect.mobiscroll.com/api/events").mock(
        return_value=httpx.Response(401, json={})
    )
    respx.post("https://connect.mobiscroll.com/api/oauth/token").mock(
        return_value=httpx.Response(401, json={"message": "revoked"})
    )
    with pytest.raises(AuthenticationError):
        client.events.list()


@respx.mock
def test_no_refresh_token_propagates_auth_error(client):
    client.auth.set_credentials(TokenResponse(access_token="old"))  # no refresh_token
    respx.get("https://connect.mobiscroll.com/api/events").mock(
        return_value=httpx.Response(401, json={"message": "expired"})
    )
    with pytest.raises(AuthenticationError):
        client.events.list()
