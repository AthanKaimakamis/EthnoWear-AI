import asyncio
from unittest.mock import AsyncMock

import httpx
import pytest

from ethnowear_worker_common.api.errors import (
    WorkerApiContractError,
    WorkerApiRequestError,
)
from ethnowear_vision_worker import app


@pytest.mark.asyncio
async def test_dependencies_are_ready_after_api_and_model_checks() -> None:
    api_client = AsyncMock()
    ollama_client = AsyncMock()

    ready = await app.wait_for_dependencies(
        api_client=api_client,
        ollama_client=ollama_client,
        stop_event=asyncio.Event(),
        minimum_delay=1.0,
        maximum_delay=10.0,
    )

    assert ready is True
    api_client.health.assert_awaited_once_with()
    ollama_client.resolve_model_version.assert_awaited_once_with()


@pytest.mark.asyncio
async def test_dependencies_retry_temporary_transport_failure(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    api_client = AsyncMock()
    api_client.health.side_effect = [
        httpx.ConnectError("temporarily unavailable"),
        None,
    ]
    ollama_client = AsyncMock()
    wait = AsyncMock(return_value=False)
    monkeypatch.setattr(app, "wait_for_stop", wait)
    monkeypatch.setattr(app, "bounded_jitter", lambda delay: delay)

    ready = await app.wait_for_dependencies(
        api_client=api_client,
        ollama_client=ollama_client,
        stop_event=asyncio.Event(),
        minimum_delay=1.0,
        maximum_delay=10.0,
    )

    assert ready is True
    assert api_client.health.await_count == 2
    wait.assert_awaited_once_with(wait.await_args.args[0], 1.0)


@pytest.mark.asyncio
async def test_dependencies_retry_rate_limit_and_server_errors(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    api_client = AsyncMock()
    api_client.health.side_effect = [
        WorkerApiRequestError(429, "RATE_LIMITED", "Retry later"),
        WorkerApiRequestError(503, "UNAVAILABLE", "Retry later"),
        None,
    ]
    wait = AsyncMock(return_value=False)
    monkeypatch.setattr(app, "wait_for_stop", wait)
    monkeypatch.setattr(app, "bounded_jitter", lambda delay: delay)

    ready = await app.wait_for_dependencies(
        api_client=api_client,
        ollama_client=AsyncMock(),
        stop_event=asyncio.Event(),
        minimum_delay=1.0,
        maximum_delay=10.0,
    )

    assert ready is True
    assert [call.args[1] for call in wait.await_args_list] == [1.0, 2.0]


@pytest.mark.asyncio
async def test_dependencies_propagate_non_retryable_api_error() -> None:
    api_client = AsyncMock()
    api_client.health.side_effect = WorkerApiRequestError(
        401,
        "UNAUTHORIZED",
        "Invalid worker credentials",
    )

    with pytest.raises(WorkerApiRequestError) as captured:
        await app.wait_for_dependencies(
            api_client=api_client,
            ollama_client=AsyncMock(),
            stop_event=asyncio.Event(),
            minimum_delay=1.0,
            maximum_delay=10.0,
        )

    assert captured.value.status_code == 401


@pytest.mark.asyncio
async def test_dependencies_propagate_invalid_contract() -> None:
    ollama_client = AsyncMock()
    ollama_client.resolve_model_version.side_effect = WorkerApiContractError(
        "invalid model response"
    )

    with pytest.raises(WorkerApiContractError):
        await app.wait_for_dependencies(
            api_client=AsyncMock(),
            ollama_client=ollama_client,
            stop_event=asyncio.Event(),
            minimum_delay=1.0,
            maximum_delay=10.0,
        )


@pytest.mark.asyncio
async def test_dependencies_stop_cleanly_during_backoff(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    ollama_client = AsyncMock()
    ollama_client.resolve_model_version.side_effect = RuntimeError(
        "model unavailable"
    )
    wait = AsyncMock(return_value=True)
    monkeypatch.setattr(app, "wait_for_stop", wait)

    ready = await app.wait_for_dependencies(
        api_client=AsyncMock(),
        ollama_client=ollama_client,
        stop_event=asyncio.Event(),
        minimum_delay=1.0,
        maximum_delay=10.0,
    )

    assert ready is False
