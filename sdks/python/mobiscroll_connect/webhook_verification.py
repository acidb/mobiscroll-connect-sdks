"""Standard Webhooks ``v1a`` (Ed25519) signature verification for Mobiscroll Connect
deliveries. Pure functions — no network access."""

from __future__ import annotations

import base64
import binascii
import json
import re
import time
from collections.abc import Iterable, Mapping
from typing import Any, Union

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey

from .exceptions import WebhookVerificationError
from .models import WebhookDelivery

WEBHOOK_TOLERANCE_SECONDS = 300
WEBHOOK_KEYS_PATH = "/.well-known/webhook-keys"
PUBLIC_KEY_PREFIX = "whpk_"

_SIGNATURE_VERSION = "v1a"
_TIMESTAMP_PATTERN = re.compile(r"[0-9]+")

WebhookPayload = Union[str, bytes, bytearray, memoryview]


def _to_bytes(payload: object) -> bytes:
    if isinstance(payload, str):
        return payload.encode("utf-8")
    if isinstance(payload, (bytes, bytearray, memoryview)):
        return bytes(payload)
    raise WebhookVerificationError(
        "Pass the raw request body as bytes or str, not a parsed object",
        "invalid_payload",
    )


def _read_header(headers: Mapping[str, Any], name: str) -> str | None:
    value = headers.get(name)
    if value is None:
        for key in headers:
            if isinstance(key, str) and key.lower() == name:
                value = headers[key]
                break
    if isinstance(value, (list, tuple)):
        value = value[0] if value else None
    return value if isinstance(value, str) else None


def _b64decode(value: str) -> bytes | None:
    try:
        return base64.b64decode(value)
    except (binascii.Error, ValueError):
        return None


def _parse_public_key(value: object) -> Ed25519PublicKey | None:
    if not isinstance(value, str):
        return None
    encoded = value.strip()
    if encoded.startswith(PUBLIC_KEY_PREFIX):
        encoded = encoded[len(PUBLIC_KEY_PREFIX) :]
    raw = _b64decode(encoded)
    if raw is None or len(raw) != 32:
        return None
    try:
        return Ed25519PublicKey.from_public_bytes(raw)
    except ValueError:
        return None


def _matches(signature: bytes, content: bytes, keys: list[Ed25519PublicKey]) -> bool:
    for key in keys:
        try:
            key.verify(signature, content)
            return True
        except InvalidSignature:
            continue
    return False


def verify_webhook_signature(
    payload: WebhookPayload,
    headers: Mapping[str, Any],
    public_keys: Iterable[str],
    *,
    tolerance_seconds: float = WEBHOOK_TOLERANCE_SECONDS,
    now: float | None = None,
) -> None:
    """Verify the Standard Webhooks ``v1a`` (Ed25519) signature of a Mobiscroll Connect
    delivery against the given public keys, without fetching anything.

    Use it with a pinned key in handlers that cannot make outbound requests. Otherwise
    prefer ``client.webhooks.verify_webhook()``, which fetches and refreshes the keys.

    :param payload: The raw request body, exactly as received (``bytes`` or ``str``).
    :param headers: The request headers — any mapping; lookup is case-insensitive.
        Must include ``webhook-id``, ``webhook-timestamp`` and ``webhook-signature``.
    :param public_keys: ``whpk_`` public keys; the delivery is genuine if any
        signature verifies against any key.
    :param tolerance_seconds: Maximum age of ``webhook-timestamp`` in either direction.
    :param now: Current Unix time in seconds; defaults to the system clock.
    :raises WebhookVerificationError: When the delivery is not genuine or cannot be checked.

    Example::

        verify_webhook_signature(request.get_data(), request.headers, [PINNED_KEY])
    """
    body = _to_bytes(payload)
    webhook_id = _read_header(headers, "webhook-id")
    timestamp = _read_header(headers, "webhook-timestamp")
    signature_header = _read_header(headers, "webhook-signature")
    if not webhook_id or not timestamp or not signature_header:
        raise WebhookVerificationError(
            "Missing webhook-id, webhook-timestamp or webhook-signature header",
            "missing_headers",
        )

    if not _TIMESTAMP_PATTERN.fullmatch(timestamp):
        raise WebhookVerificationError("Invalid webhook-timestamp header", "invalid_timestamp")
    current = int(time.time()) if now is None else now
    # The length guard keeps int() away from its digit limit (3.11+); such values are stale anyway.
    if len(timestamp.lstrip("0")) > 15 or abs(current - int(timestamp)) > tolerance_seconds:
        raise WebhookVerificationError(
            "Webhook timestamp is outside the allowed tolerance",
            "timestamp_out_of_tolerance",
        )

    keys = [key for key in map(_parse_public_key, public_keys) if key is not None]
    if not keys:
        raise WebhookVerificationError("No valid webhook public keys available", "no_public_keys")

    signed_content = f"{webhook_id}.{timestamp}.".encode() + body
    for entry in signature_header.split(" "):
        version, separator, encoded = entry.partition(",")
        if not separator or version != _SIGNATURE_VERSION:
            continue
        signature = _b64decode(encoded)
        if signature is not None and len(signature) == 64 and _matches(
            signature, signed_content, keys
        ):
            return

    raise WebhookVerificationError("No webhook signature matched", "no_matching_signature")


def parse_webhook_delivery(payload: WebhookPayload) -> WebhookDelivery:
    """Parse a verified delivery body. Raises ``WebhookVerificationError`` with reason
    ``invalid_payload`` when it is not a JSON object."""
    try:
        data = json.loads(_to_bytes(payload))
        if not isinstance(data, Mapping):
            raise ValueError("not an object")
        return WebhookDelivery.from_dict(data)
    except (ValueError, TypeError) as exc:
        raise WebhookVerificationError(
            "Webhook payload is not valid JSON", "invalid_payload"
        ) from exc
