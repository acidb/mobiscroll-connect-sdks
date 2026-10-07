from __future__ import annotations

import asyncio
import base64
import json
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import httpx
import pytest
import respx
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat

from mobiscroll_connect import (
    MobiscrollConnectClient,
    MobiscrollConnectError,
    WebhookEvent,
    WebhookVerificationError,
    verify_webhook_signature,
)
from mobiscroll_connect._internal import webhook_keys
from mobiscroll_connect._internal.webhook_keys import WebhookKeyStore
from mobiscroll_connect.aio import AsyncMobiscrollConnectClient

VECTORS = json.loads((Path(__file__).parent / "fixtures" / "webhook-vectors.json").read_text("utf-8"))
KEYS = VECTORS["keys"]
KEYS_URL = "https://connect.mobiscroll.com/.well-known/webhook-keys"


def by_name(name: str) -> dict:
    return next(case for case in VECTORS["cases"] if case["name"] == name)


VALID = by_name("valid single signature")


def keys_response(keys: list[str], cache_control: str = "public, max-age=3600") -> httpx.Response:
    return httpx.Response(
        200,
        json={"keys": [{"id": k, "alg": "ed25519", "key": k, "status": "active"} for k in keys]},
        headers={"cache-control": cache_control},
    )


def sign(body: str, private_key: Ed25519PrivateKey, timestamp: int) -> dict[str, str]:
    webhook_id = "msg_test"
    signature = private_key.sign(f"{webhook_id}.{timestamp}.".encode() + body.encode())
    return {
        "webhook-id": webhook_id,
        "webhook-timestamp": str(timestamp),
        "webhook-signature": "v1a," + base64.b64encode(signature).decode(),
    }


def public_key_of(private_key: Ed25519PrivateKey) -> str:
    raw = private_key.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw)
    return "whpk_" + base64.b64encode(raw).decode()


# --- pure function -------------------------------------------------------------------


@pytest.mark.parametrize("case", VECTORS["cases"], ids=[c["name"] for c in VECTORS["cases"]])
def test_vectors(case):
    def run():
        verify_webhook_signature(case["body"], case["headers"], case["publicKeys"], now=case["now"])

    if case["valid"]:
        run()
    else:
        with pytest.raises(WebhookVerificationError):
            run()


def test_accepts_bytes_and_header_names_in_any_casing():
    headers = {k.upper(): [v] for k, v in VALID["headers"].items()}
    for body in (VALID["body"].encode(), bytearray(VALID["body"].encode()), memoryview(VALID["body"].encode())):
        verify_webhook_signature(body, headers, VALID["publicKeys"], now=VALID["now"])


def test_accepts_a_case_insensitive_headers_mapping():
    headers = httpx.Headers({k.title(): v for k, v in VALID["headers"].items()})
    verify_webhook_signature(VALID["body"], headers, VALID["publicKeys"], now=VALID["now"])


def test_rejects_a_parsed_body():
    with pytest.raises(WebhookVerificationError) as exc_info:
        verify_webhook_signature(
            json.loads(VALID["body"]), VALID["headers"], VALID["publicKeys"], now=VALID["now"]
        )
    assert exc_info.value.reason == "invalid_payload"


@pytest.mark.parametrize(
    ("name", "reason"),
    [
        ("timestamp 301 s old, stale", "timestamp_out_of_tolerance"),
        ("timestamp 301 s in the future", "timestamp_out_of_tolerance"),
        ("non-numeric timestamp", "invalid_timestamp"),
        ("missing webhook-id", "missing_headers"),
        ("no public keys", "no_public_keys"),
        ("tampered body", "no_matching_signature"),
        ("only a v1 HMAC entry", "no_matching_signature"),
        ("malformed base64 signature", "no_matching_signature"),
    ],
)
def test_reports_why_verification_failed(name, reason):
    case = by_name(name)
    with pytest.raises(WebhookVerificationError) as exc_info:
        verify_webhook_signature(case["body"], case["headers"], case["publicKeys"], now=case["now"])
    assert exc_info.value.reason == reason
    assert exc_info.value.code == "WEBHOOK_VERIFICATION_ERROR"
    assert isinstance(exc_info.value, MobiscrollConnectError)


def test_rejects_unicode_digits_and_huge_timestamps():
    fullwidth = "".join(chr(0xFF10 + int(d)) for d in "1790000000")
    for timestamp in (fullwidth, "1790000000\n"):
        headers = {**VALID["headers"], "webhook-timestamp": timestamp}
        with pytest.raises(WebhookVerificationError) as exc_info:
            verify_webhook_signature(VALID["body"], headers, VALID["publicKeys"], now=VALID["now"])
        assert exc_info.value.reason == "invalid_timestamp"

    headers = {**VALID["headers"], "webhook-timestamp": "9" * 5000}
    with pytest.raises(WebhookVerificationError) as exc_info:
        verify_webhook_signature(VALID["body"], headers, VALID["publicKeys"], now=VALID["now"])
    assert exc_info.value.reason == "timestamp_out_of_tolerance"


def test_honours_a_custom_tolerance():
    stale = by_name("timestamp 301 s old, stale")
    verify_webhook_signature(
        stale["body"], stale["headers"], stale["publicKeys"], now=stale["now"], tolerance_seconds=301
    )


# --- client.webhooks.verify_webhook ----------------------------------------------------


@pytest.fixture
def clock(monkeypatch):
    state = {"now": float(VALID["now"])}
    monkeypatch.setattr(webhook_keys, "_clock", lambda: state["now"])
    monkeypatch.setattr(time, "time", lambda: state["now"])
    return state


@pytest.fixture(autouse=True)
def reset_key_store():
    WebhookKeyStore.reset()
    yield
    WebhookKeyStore.reset()


def make_client(webhook_public_key: str | None = None, **kwargs) -> MobiscrollConnectClient:
    return MobiscrollConnectClient(
        "id", "secret", "https://app/cb", webhook_public_key=webhook_public_key, **kwargs
    )


def make_async_client(webhook_public_key: str | None = None) -> AsyncMobiscrollConnectClient:
    return AsyncMobiscrollConnectClient(
        "id", "secret", "https://app/cb", webhook_public_key=webhook_public_key
    )


@respx.mock
def test_fetches_keys_lazily_from_the_api_origin_and_parses_the_delivery(clock):
    route = respx.get(KEYS_URL).mock(return_value=keys_response([KEYS["active"]]))
    client = make_client()
    assert route.call_count == 0

    delivery = client.webhooks.verify_webhook(VALID["body"], VALID["headers"])

    assert route.call_count == 1
    assert "authorization" not in route.calls.last.request.headers
    assert delivery.user_id == "user_42"
    assert delivery.provider == "google"
    assert isinstance(delivery.events[0], WebhookEvent)
    assert delivery.events[0].title == "Café meeting ☕ — Zoë"
    assert delivery.events[0].provider == "google"
    assert delivery.events[0].calendar_id == "primary"


@respx.mock
def test_derives_the_keys_url_from_a_custom_base_url(clock):
    route = respx.get("https://connect-dev.example.com/.well-known/webhook-keys").mock(
        return_value=keys_response([KEYS["active"]])
    )
    client = make_client(base_url="https://connect-dev.example.com/api")
    client.webhooks.verify_webhook(VALID["body"], VALID["headers"])
    assert route.call_count == 1


@respx.mock
def test_shares_one_cache_across_clients_and_honours_max_age(clock):
    route = respx.get(KEYS_URL).mock(return_value=keys_response([KEYS["active"]], "max-age=120"))

    make_client().webhooks.verify_webhook(VALID["body"], VALID["headers"])
    make_client().webhooks.verify_webhook(VALID["body"], VALID["headers"])
    assert route.call_count == 1

    clock["now"] += 121
    make_client().webhooks.verify_webhook(VALID["body"], VALID["headers"])
    assert route.call_count == 2


@respx.mock
async def test_shares_the_cache_between_sync_and_async_clients(clock):
    route = respx.get(KEYS_URL).mock(return_value=keys_response([KEYS["active"]]))
    make_client().webhooks.verify_webhook(VALID["body"], VALID["headers"])
    await make_async_client().webhooks.verify_webhook(VALID["body"], VALID["headers"])
    assert route.call_count == 1


@respx.mock
def test_refetches_once_after_a_rotation(clock):
    route = respx.get(KEYS_URL).mock(
        side_effect=[keys_response([KEYS["unrelated"]]), keys_response([KEYS["active"]])]
    )
    clock["now"] -= 61
    assert WebhookKeyStore.for_url(KEYS_URL).get_keys() == [KEYS["unrelated"]]
    clock["now"] += 61

    delivery = make_client().webhooks.verify_webhook(VALID["body"], VALID["headers"])

    assert delivery.calendar_id == "primary"
    assert route.call_count == 2


@respx.mock
async def test_async_refetches_once_after_a_rotation(clock):
    route = respx.get(KEYS_URL).mock(
        side_effect=[keys_response([KEYS["unrelated"]]), keys_response([KEYS["active"]])]
    )
    clock["now"] -= 61
    await WebhookKeyStore.for_url(KEYS_URL).get_keys_async()
    clock["now"] += 61

    delivery = await make_async_client().webhooks.verify_webhook(VALID["body"], VALID["headers"])

    assert delivery.calendar_id == "primary"
    assert route.call_count == 2


@respx.mock
def test_does_not_refetch_more_than_once_a_minute_on_forged_deliveries(clock):
    route = respx.get(KEYS_URL).mock(return_value=keys_response([KEYS["active"]]))
    client = make_client()
    forged = by_name("tampered body")

    for _ in range(2):
        with pytest.raises(WebhookVerificationError) as exc_info:
            client.webhooks.verify_webhook(forged["body"], forged["headers"])
        assert exc_info.value.reason == "no_matching_signature"
    assert route.call_count == 1


@respx.mock
def test_does_not_refetch_on_non_key_failures(clock):
    route = respx.get(KEYS_URL).mock(return_value=keys_response([KEYS["active"]]))
    stale = {**VALID["headers"], "webhook-timestamp": str(VALID["now"] - 301)}
    with pytest.raises(WebhookVerificationError) as exc_info:
        make_client().webhooks.verify_webhook(VALID["body"], stale)
    assert exc_info.value.reason == "timestamp_out_of_tolerance"
    assert route.call_count == 1


@respx.mock
def test_falls_back_to_the_pinned_key_when_the_endpoint_is_unreachable(clock):
    respx.get(KEYS_URL).mock(side_effect=httpx.ConnectError("refused"))
    delivery = make_client(KEYS["active"]).webhooks.verify_webhook(VALID["body"], VALID["headers"])
    assert delivery.provider == "google"


@respx.mock
async def test_async_falls_back_to_the_pinned_key_on_a_server_error(clock):
    respx.get(KEYS_URL).mock(return_value=httpx.Response(503))
    client = make_async_client(KEYS["active"])
    delivery = await client.webhooks.verify_webhook(VALID["body"], VALID["headers"])
    assert delivery.provider == "google"


@respx.mock
def test_ignores_the_pinned_key_while_fetched_keys_exist(clock):
    respx.get(KEYS_URL).mock(return_value=keys_response([KEYS["unrelated"]]))
    with pytest.raises(WebhookVerificationError) as exc_info:
        make_client(KEYS["active"]).webhooks.verify_webhook(VALID["body"], VALID["headers"])
    assert exc_info.value.reason == "no_matching_signature"


@respx.mock
def test_keeps_the_last_good_keys_when_a_refresh_fails(clock):
    route = respx.get(KEYS_URL).mock(return_value=keys_response([KEYS["active"]]))
    client = make_client()
    client.webhooks.verify_webhook(VALID["body"], VALID["headers"])

    route.mock(side_effect=httpx.ReadTimeout("timeout"))
    clock["now"] += 3601
    later = {**VALID["headers"], "webhook-timestamp": str(VALID["now"] + 3601)}
    with pytest.raises(WebhookVerificationError) as exc_info:
        client.webhooks.verify_webhook(VALID["body"], later)
    assert exc_info.value.reason == "no_matching_signature"
    assert route.call_count == 2
    assert WebhookKeyStore.for_url(KEYS_URL).keys == [KEYS["active"]]


@respx.mock
def test_ignores_responses_without_usable_keys(clock):
    respx.get(KEYS_URL).mock(
        return_value=httpx.Response(
            200,
            json={
                "keys": [
                    {"alg": "rsa", "key": "whpk_" + "A" * 43 + "="},
                    {"alg": "ed25519", "key": "not-a-whpk-key"},
                ]
            },
        )
    )
    with pytest.raises(WebhookVerificationError) as exc_info:
        make_client().webhooks.verify_webhook(VALID["body"], VALID["headers"])
    assert exc_info.value.reason == "no_public_keys"


@respx.mock
def test_fails_with_no_public_keys_when_nothing_is_available(clock):
    route = respx.get(KEYS_URL).mock(side_effect=httpx.ConnectError("refused"))
    with pytest.raises(WebhookVerificationError) as exc_info:
        make_client().webhooks.verify_webhook(VALID["body"], VALID["headers"])
    assert exc_info.value.reason == "no_public_keys"
    assert route.call_count == 1


@respx.mock
async def test_concurrent_async_calls_share_one_fetch(clock):
    async def slow_keys(request):
        await asyncio.sleep(0.05)
        return keys_response([KEYS["active"]])

    route = respx.get(KEYS_URL).mock(side_effect=slow_keys)
    client = make_async_client()
    deliveries = await asyncio.gather(
        *(client.webhooks.verify_webhook(VALID["body"], VALID["headers"]) for _ in range(3))
    )
    assert route.call_count == 1
    assert [d.user_id for d in deliveries] == ["user_42"] * 3


@respx.mock
def test_concurrent_threads_share_one_fetch(clock):
    started = threading.Event()

    def slow_keys(request):
        started.set()
        time.sleep(0.05)
        return keys_response([KEYS["active"]])

    route = respx.get(KEYS_URL).mock(side_effect=slow_keys)
    client = make_client()
    with ThreadPoolExecutor(max_workers=4) as pool:
        deliveries = list(
            pool.map(lambda _: client.webhooks.verify_webhook(VALID["body"], VALID["headers"]), range(4))
        )
    assert started.is_set()
    assert route.call_count == 1
    assert [d.user_id for d in deliveries] == ["user_42"] * 4


@respx.mock
def test_parses_the_full_delivery_shape(clock):
    private_key = Ed25519PrivateKey.generate()
    respx.get(KEYS_URL).mock(return_value=keys_response([public_key_of(private_key)]))
    body = json.dumps(
        {
            "provider": "microsoft",
            "userId": "user_1",
            "calendarId": "cal_1",
            "changeType": "mixed",
            "timestamp": "2026-10-06T10:00:00.000Z",
            "events": [
                {
                    "provider": "microsoft",
                    "id": "evt_1",
                    "calendarId": "cal_1",
                    "title": "Standup",
                    "start": "2026-10-07T09:00:00.000Z",
                    "end": "2026-10-07T09:15:00.000Z",
                    "changeType": "created",
                    "unknownField": True,
                }
            ],
            "metadata": {"channelId": "chan_1", "eventCount": 1, "isInitialSync": False},
        }
    )
    delivery = make_client().webhooks.verify_webhook(
        body.encode(), sign(body, private_key, VALID["now"])
    )
    assert delivery.change_type == "mixed"
    assert delivery.timestamp == "2026-10-06T10:00:00.000Z"
    assert delivery.metadata.channel_id == "chan_1"
    assert delivery.metadata.event_count == 1
    assert delivery.metadata.is_initial_sync is False
    assert delivery.events[0].change_type == "created"
    assert delivery.events[0].start.isoformat() == "2026-10-07T09:00:00+00:00"


@respx.mock
def test_rejects_a_genuine_body_that_is_not_json(clock):
    private_key = Ed25519PrivateKey.generate()
    respx.get(KEYS_URL).mock(return_value=keys_response([public_key_of(private_key)]))
    for body in ("not json", "[1, 2]"):
        with pytest.raises(WebhookVerificationError) as exc_info:
            make_client().webhooks.verify_webhook(body, sign(body, private_key, VALID["now"]))
        assert exc_info.value.reason == "invalid_payload"
