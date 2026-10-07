"""Process-wide cache of the webhook public keys published at a keys URL.

One store per keys URL, shared by every sync and async client in the process.
It follows the endpoint's ``Cache-Control: max-age``, attempts a fetch at most once
a minute, and keeps the last good keys when a fetch fails.

A fetch in flight is represented by a ``concurrent.futures.Future`` so that threads
(``future.result()``) and coroutines on any event loop (``asyncio.wrap_future``)
can all wait on the same fetch.
"""

from __future__ import annotations

import asyncio
import re
import threading
import time
from collections.abc import Mapping
from concurrent.futures import Future
from typing import Any, ClassVar
from urllib.parse import urlsplit, urlunsplit

import httpx

from ..exceptions import WebhookVerificationError
from ..webhook_verification import PUBLIC_KEY_PREFIX, WEBHOOK_KEYS_PATH

DEFAULT_MAX_AGE_SECONDS = 60 * 60
MAX_MAX_AGE_SECONDS = 24 * 60 * 60
MIN_REFETCH_INTERVAL_SECONDS = 60
FETCH_TIMEOUT_SECONDS = 10.0

_MAX_AGE_PATTERN = re.compile(r"max-age=(\d+)", re.IGNORECASE)


def _clock() -> float:
    return time.monotonic()


def webhook_keys_url(base_url: str) -> str:
    """The keys URL on the origin of the API base URL."""
    parts = urlsplit(base_url)
    return urlunsplit((parts.scheme, parts.netloc, WEBHOOK_KEYS_PATH, "", ""))


def keys_or_pinned(keys: list[str], pinned_key: str | None) -> list[str]:
    """The pinned key stands in only when nothing was fetched — never merged, so a
    key retired by an emergency rotation stops verifying."""
    return keys if keys or not pinned_key else [pinned_key]


def is_retryable(error: WebhookVerificationError) -> bool:
    return error.reason in ("no_matching_signature", "no_public_keys")


def _parse_max_age(cache_control: str | None) -> float:
    match = _MAX_AGE_PATTERN.search(cache_control or "")
    if match is None:
        return DEFAULT_MAX_AGE_SECONDS
    return min(int(match.group(1)), MAX_MAX_AGE_SECONDS)


def _extract_keys(data: Any) -> list[str]:
    entries = data.get("keys") if isinstance(data, Mapping) else None
    if not isinstance(entries, list):
        return []
    keys: list[str] = []
    for entry in entries:
        if not isinstance(entry, Mapping):
            continue
        key = entry.get("key")
        alg = entry.get("alg")
        if not isinstance(key, str) or not key.startswith(PUBLIC_KEY_PREFIX):
            continue
        if alg and (not isinstance(alg, str) or alg.lower() != "ed25519"):
            continue
        keys.append(key)
    return keys


class WebhookKeyStore:
    """Cached keys for one keys URL. Obtain instances with :meth:`for_url`."""

    _stores: ClassVar[dict[str, WebhookKeyStore]] = {}
    _stores_lock: ClassVar[threading.Lock] = threading.Lock()

    def __init__(self, url: str) -> None:
        self.url = url
        self._lock = threading.Lock()
        self._keys: list[str] = []
        self._fetched_at: float | None = None
        self._last_attempt_at: float | None = None
        self._max_age: float = DEFAULT_MAX_AGE_SECONDS
        self._in_flight: Future[None] | None = None
        self._in_flight_thread: int | None = None

    @classmethod
    def for_url(cls, url: str) -> WebhookKeyStore:
        with cls._stores_lock:
            store = cls._stores.get(url)
            if store is None:
                store = cls._stores[url] = cls(url)
            return store

    @classmethod
    def reset(cls) -> None:
        """Clear every cached key set. For tests."""
        with cls._stores_lock:
            cls._stores.clear()

    @property
    def keys(self) -> list[str]:
        with self._lock:
            return list(self._keys)

    def can_refetch(self) -> bool:
        with self._lock:
            return self._can_refetch(_clock())

    def get_keys(self) -> list[str]:
        """Return the cached keys, fetching first when they are stale."""
        self._update(stale_only=True)
        return self.keys

    def refresh(self) -> None:
        """Fetch now if the once-a-minute limit allows, or join a fetch in flight."""
        self._update(stale_only=False)

    async def get_keys_async(self) -> list[str]:
        await self._update_async(stale_only=True)
        return self.keys

    async def refresh_async(self) -> None:
        await self._update_async(stale_only=False)

    def _can_refetch(self, now: float) -> bool:
        return (
            self._last_attempt_at is None
            or now - self._last_attempt_at >= MIN_REFETCH_INTERVAL_SECONDS
        )

    def _claim(self, *, stale_only: bool) -> tuple[Future[None] | None, bool, int | None]:
        """Join the fetch in flight, start one (``owner=True``), or do nothing."""
        now = _clock()
        with self._lock:
            if self._in_flight is not None:
                return self._in_flight, False, self._in_flight_thread
            stale = self._fetched_at is None or now - self._fetched_at > self._max_age
            if not self._can_refetch(now) or (stale_only and not stale):
                return None, False, None
            self._in_flight = Future()
            # Running futures cannot be cancelled, so a cancelled waiter never cancels the fetch.
            self._in_flight.set_running_or_notify_cancel()
            self._in_flight_thread = threading.get_ident()
            self._last_attempt_at = now
            return self._in_flight, True, self._in_flight_thread

    def _release(self, future: Future[None]) -> None:
        with self._lock:
            self._in_flight = None
            self._in_flight_thread = None
        future.set_result(None)

    def _update(self, *, stale_only: bool) -> None:
        future, owner, owner_thread = self._claim(stale_only=stale_only)
        if future is None:
            return
        if owner:
            try:
                self._store(self._fetch())
            finally:
                self._release(future)
        elif owner_thread != threading.get_ident():
            # Same thread means an async fetch on this thread's event loop; blocking here
            # would deadlock it, so use the keys as they are.
            future.result()

    async def _update_async(self, *, stale_only: bool) -> None:
        future, owner, _ = self._claim(stale_only=stale_only)
        if future is None:
            return
        if owner:
            try:
                self._store(await self._fetch_async())
            finally:
                self._release(future)
        else:
            await asyncio.wrap_future(future)

    def _fetch(self) -> httpx.Response | None:
        try:
            return httpx.get(self.url, timeout=FETCH_TIMEOUT_SECONDS, follow_redirects=True)
        except Exception:
            return None

    async def _fetch_async(self) -> httpx.Response | None:
        try:
            async with httpx.AsyncClient(
                timeout=FETCH_TIMEOUT_SECONDS, follow_redirects=True
            ) as http:
                return await http.get(self.url)
        except Exception:
            return None

    def _store(self, response: httpx.Response | None) -> None:
        if response is None or not response.is_success:
            return
        try:
            keys = _extract_keys(response.json())
        except ValueError:
            return
        if not keys:
            return
        with self._lock:
            self._keys = keys
            self._fetched_at = _clock()
            self._max_age = _parse_max_age(response.headers.get("cache-control"))
