from __future__ import annotations

from typing import List, Mapping

from ..api_client import ApiClient
from ..models import Calendar


class Calendars:
    """Calendar listing across all connected providers."""

    def __init__(self, api_client: ApiClient) -> None:
        self._api = api_client

    def list(self) -> List[Calendar]:
        data = self._api.get("calendars")
        if not isinstance(data, list):
            return []
        return [Calendar.from_dict(c) for c in data if isinstance(c, Mapping)]
