"""Full event CRUD example: create, update, delete, recurring instance."""

from __future__ import annotations

import os
from datetime import datetime, timedelta, timezone

from mobiscroll_connect import MobiscrollConnectClient, TokenResponse


def main() -> None:
    with MobiscrollConnectClient(
        client_id=os.environ["MOBISCROLL_CLIENT_ID"],
        client_secret=os.environ["MOBISCROLL_CLIENT_SECRET"],
        redirect_uri=os.environ["MOBISCROLL_REDIRECT_URI"],
    ) as client:
        client.auth.set_credentials(TokenResponse(
            access_token=os.environ["MOBISCROLL_ACCESS_TOKEN"],
            refresh_token=os.environ.get("MOBISCROLL_REFRESH_TOKEN"),
        ))

        start = datetime.now(timezone.utc) + timedelta(days=1)
        end = start + timedelta(hours=1)

        # Create
        event = client.events.create({
            "provider": "google",
            "calendar_id": "primary",
            "title": "Team standup",
            "start": start,
            "end": end,
            "description": "Daily sync",
            "location": "Online",
        })
        print(f"Created: {event.id}")

        # Update
        updated = client.events.update({
            "provider": "google",
            "calendar_id": "primary",
            "event_id": event.id,
            "title": "Team standup (rescheduled)",
            "start": start + timedelta(hours=2),
            "end": end + timedelta(hours=2),
        })
        print(f"Updated: {updated.title}")

        # Delete
        client.events.delete({
            "provider": "google",
            "calendar_id": "primary",
            "event_id": event.id,
        })
        print("Deleted")


if __name__ == "__main__":
    main()
