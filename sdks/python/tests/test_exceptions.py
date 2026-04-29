import httpx
import pytest

from mobiscroll_connect._internal.errors import map_response_error, map_transport_error
from mobiscroll_connect.exceptions import (
    AuthenticationError,
    MobiscrollConnectError,
    NetworkError,
    NotFoundError,
    RateLimitError,
    ServerError,
    ValidationError,
)


def _resp(status: int, json_body=None, headers=None) -> httpx.Response:
    return httpx.Response(status_code=status, json=json_body or {}, headers=headers or {})


class TestErrorMapping:
    def test_401_to_auth_error(self):
        e = map_response_error(_resp(401, {"message": "expired"}))
        assert isinstance(e, AuthenticationError)
        assert e.message == "expired"

    def test_403_also_auth(self):
        assert isinstance(map_response_error(_resp(403)), AuthenticationError)

    def test_404_to_not_found(self):
        assert isinstance(map_response_error(_resp(404)), NotFoundError)

    def test_400_to_validation_with_details(self):
        e = map_response_error(_resp(400, {"message": "bad", "details": {"title": ["required"]}}))
        assert isinstance(e, ValidationError)
        assert e.details == {"title": ["required"]}

    def test_422_also_validation(self):
        assert isinstance(map_response_error(_resp(422)), ValidationError)

    def test_429_with_retry_after(self):
        e = map_response_error(_resp(429, headers={"retry-after": "30"}))
        assert isinstance(e, RateLimitError)
        assert e.retry_after == 30

    def test_429_without_retry_after(self):
        e = map_response_error(_resp(429))
        assert isinstance(e, RateLimitError)
        assert e.retry_after is None

    def test_5xx_to_server_error(self):
        for status in (500, 502, 503, 504):
            e = map_response_error(_resp(status))
            assert isinstance(e, ServerError)
            assert e.status_code == status

    def test_unmapped_status(self):
        e = map_response_error(_resp(418, {"code": "TEAPOT"}))
        assert isinstance(e, MobiscrollConnectError)
        assert e.code == "TEAPOT"

    def test_transport_error(self):
        e = map_transport_error(httpx.ConnectError("dns failed"))
        assert isinstance(e, NetworkError)


class TestExceptionHierarchy:
    @pytest.mark.parametrize(
        "exc_cls",
        [AuthenticationError, NotFoundError, ValidationError, RateLimitError, ServerError, NetworkError],
    )
    def test_all_inherit_from_base(self, exc_cls):
        assert issubclass(exc_cls, MobiscrollConnectError)

    def test_validation_default_details(self):
        e = ValidationError("bad")
        assert e.details == {}

    def test_rate_limit_default_retry_after(self):
        assert RateLimitError().retry_after is None
