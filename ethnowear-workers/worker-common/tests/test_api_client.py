import asyncio

import httpx
import pytest
from pydantic import SecretStr

from ethnowear_worker_common.api.client import WorkerApiTransport
from ethnowear_worker_common.api.errors import (
    WorkerApiContractError,
    WorkerApiRequestError,
)
from ethnowear_worker_common.api.models import (
    HeartbeatResponse,
    ReadinessResponse,
)


def transport() -> WorkerApiTransport:
    return WorkerApiTransport(
        api_base_url="http://backend:8080/",
        worker_id="worker-1",
        api_token="secret-token",
        http_client=httpx.AsyncClient(),
    )


def response(status: int, json: object) -> httpx.Response:
    return httpx.Response(
        status,
        json=json,
        request=httpx.Request("GET", "http://backend:8080/test"),
    )


def test_transport_builds_normalized_urls_and_paths() -> None:
    client = transport()

    assert client._url("/health") == "http://backend:8080/health"
    assert client._job_path(20, "/heartbeat") == (
        "/api/internal/worker/jobs/20/heartbeat"
    )


def test_transport_separates_service_and_claim_headers() -> None:
    client = transport()

    assert client._service_headers() == {
        "Authorization": "Worker secret-token"
    }
    assert client._claim_headers(SecretStr("claim-token")) == {
        "Authorization": "Worker secret-token",
        "X-Worker-Id": "worker-1",
        "X-Worker-Claim-Token": "claim-token",
    }


def test_health_uses_service_authentication_and_validates_readiness() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/api/internal/worker/health"
        assert request.headers["Authorization"] == "Worker secret-token"
        assert "X-Worker-Claim-Token" not in request.headers
        return httpx.Response(
            200,
            json={
                "status": "READY",
                "workerApiEnabled": True,
            },
        )

    async def execute() -> ReadinessResponse:
        async with httpx.AsyncClient(
            transport=httpx.MockTransport(handler)
        ) as http_client:
            client = WorkerApiTransport(
                api_base_url="http://backend:8080",
                worker_id="worker-1",
                api_token="secret-token",
                http_client=http_client,
            )
            return await client.health()

    readiness = asyncio.run(execute())

    assert readiness.worker_api_enabled is True


def test_transport_parses_valid_contract_response() -> None:
    parsed = transport()._parse_response(
        response(
            200,
            {
                "leaseExpiresAt": "2026-08-25T12:00:00Z",
                "cancellationRequested": False,
            },
        ),
        HeartbeatResponse,
        "heartbeat",
    )

    assert parsed.cancellation_requested is False


def test_transport_rejects_invalid_contract_response() -> None:
    with pytest.raises(WorkerApiContractError, match="invalid heartbeat"):
        transport()._parse_response(
            response(200, {"unexpected": True}),
            HeartbeatResponse,
            "heartbeat",
        )


def test_transport_uses_safe_api_error_fields() -> None:
    error = transport()._api_error(
        response(
            409,
            {
                "code": "STALE_CLAIM",
                "message": "The claim is stale",
                "stackTrace": "must not be copied",
            },
        )
    )

    assert isinstance(error, WorkerApiRequestError)
    assert error.status_code == 409
    assert error.code == "STALE_CLAIM"
    assert "stackTrace" not in str(error)
