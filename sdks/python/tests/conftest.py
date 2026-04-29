from __future__ import annotations

import pytest

from mobiscroll_connect import MobiscrollConnectClient, TokenResponse
from mobiscroll_connect.aio import AsyncMobiscrollConnectClient


@pytest.fixture
def credentials() -> TokenResponse:
    return TokenResponse(
        access_token="test-access-token",
        refresh_token="test-refresh-token",
        expires_in=3600,
    )


@pytest.fixture
def client(credentials: TokenResponse) -> MobiscrollConnectClient:
    c = MobiscrollConnectClient(
        client_id="test-client-id",
        client_secret="test-client-secret",
        redirect_uri="https://app.example/callback",
    )
    c.auth.set_credentials(credentials)
    yield c
    c.close()


@pytest.fixture
async def async_client(credentials: TokenResponse) -> AsyncMobiscrollConnectClient:
    c = AsyncMobiscrollConnectClient(
        client_id="test-client-id",
        client_secret="test-client-secret",
        redirect_uri="https://app.example/callback",
    )
    c.auth.set_credentials(credentials)
    yield c
    await c.aclose()
