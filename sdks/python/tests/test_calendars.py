import httpx
import pytest
import respx

from mobiscroll_connect import MobiscrollConnectClient, TokenResponse


@pytest.fixture
def client():
    c = MobiscrollConnectClient(
        client_id="cid", client_secret="csecret", redirect_uri="https://app/cb"
    )
    c.auth.set_credentials(TokenResponse(access_token="t"))
    yield c
    c.close()


@respx.mock
def test_list_calendars(client):
    respx.get("https://connect.mobiscroll.com/api/calendars").mock(
        return_value=httpx.Response(200, json=[
            {"provider": "google", "id": "primary", "title": "Personal", "timeZone": "UTC"},
            {"provider": "microsoft", "id": "calendar", "title": "Work", "timeZone": "UTC"},
        ])
    )
    calendars = client.calendars.list()
    assert len(calendars) == 2
    assert calendars[0].provider == "google"
    assert calendars[1].title == "Work"


@respx.mock
def test_list_empty_response(client):
    respx.get("https://connect.mobiscroll.com/api/calendars").mock(
        return_value=httpx.Response(200, json=[])
    )
    assert client.calendars.list() == []
