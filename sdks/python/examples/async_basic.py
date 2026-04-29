"""Async example: list events across pages with iter_all()."""

from __future__ import annotations

import asyncio
import os
from datetime import datetime, timedelta, timezone

from dotenv import load_dotenv

load_dotenv()

from mobiscroll_connect import TokenResponse
from mobiscroll_connect.aio import AsyncMobiscrollConnectClient


async def main() -> None:
    async with AsyncMobiscrollConnectClient(
        client_id=os.environ["MOBISCROLL_CLIENT_ID"],
        client_secret=os.environ["MOBISCROLL_CLIENT_SECRET"],
        redirect_uri=os.environ["MOBISCROLL_REDIRECT_URI"],
    ) as client:
        # Restore credentials from your store (DB / session).
        client.auth.set_credentials(TokenResponse(
            access_token=os.environ["MOBISCROLL_ACCESS_TOKEN"],
            refresh_token=os.environ.get("MOBISCROLL_REFRESH_TOKEN"),
        ))

        now = datetime.now(timezone.utc)
        count = 0
        async for event in client.events.iter_all(
            start=now, end=now + timedelta(days=90), page_size=250
        ):
            count += 1
            if count <= 5:
                print(f"  {event.start} — {event.title}")

        print(f"Total events: {count}")


if __name__ == "__main__":
    asyncio.run(main())
