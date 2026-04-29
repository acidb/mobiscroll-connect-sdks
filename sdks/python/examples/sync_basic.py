"""Minimal synchronous example: OAuth flow + list calendars + list events.

Run from the repo root::

    pip install -e .
    python examples/sync_basic.py
"""

from __future__ import annotations

import os
from datetime import datetime, timedelta, timezone

from dotenv import load_dotenv

load_dotenv()

from mobiscroll_connect import (
    AuthenticationError,
    MobiscrollConnectClient,
    TokenResponse,
)


def main() -> None:
    client_id = os.environ["MOBISCROLL_CLIENT_ID"]
    client_secret = os.environ["MOBISCROLL_CLIENT_SECRET"]
    redirect_uri = os.environ["MOBISCROLL_REDIRECT_URI"]

    with MobiscrollConnectClient(client_id, client_secret, redirect_uri) as client:

        def persist_tokens(t: TokenResponse) -> None:
            # Replace with your DB / session store.
            print(f"[refresh] new access_token={t.access_token[:8]}...")

        client.on_tokens_refreshed(persist_tokens)

        # 1. Build the authorization URL — redirect the user there.
        auth_url = client.auth.generate_auth_url(user_id="user-123")
        print("Visit:", auth_url)

        # 2. After the redirect, exchange the code for tokens.
        code = input("Paste the `code` query param from the callback URL: ").strip()
        tokens = client.auth.get_token(code)
        print("Access token issued; expires_in =", tokens.expires_in)

        # 3. Make API calls.
        try:
            for calendar in client.calendars.list():
                print(f"  {calendar.provider}: {calendar.title} ({calendar.id})")

            now = datetime.now(timezone.utc)
            response = client.events.list(start=now, end=now + timedelta(days=30), page_size=100)
            print(f"Found {len(response)} events; has_more={response.has_more}")

        except AuthenticationError:
            print("Authentication failed — re-authorize the user.")


if __name__ == "__main__":
    main()
