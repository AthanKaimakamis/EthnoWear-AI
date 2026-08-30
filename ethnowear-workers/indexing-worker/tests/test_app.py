import asyncio
from unittest.mock import AsyncMock

import httpx
import pytest

from ethnowear_indexer.app import wait_for_dependencies
from ethnowear_worker_common.api.errors import WorkerApiContractError


@pytest.mark.asyncio
async def test_dependencies_are_ready_only_after_all_checks_pass() -> None:
    api_client = AsyncMock()
    vector_store = AsyncMock()

    ready = await wait_for_dependencies(
        api_client=api_client,
        vector_store=vector_store,
        stop_event=asyncio.Event(),
        minimum_delay=1.0,
        maximum_delay=10.0,
    )

    assert ready is True
    api_client.health.assert_awaited_once_with()
    vector_store.health.assert_awaited_once_with()


@pytest.mark.asyncio
async def test_dependencies_retry_temporary_transport_failure(
        monkeypatch: pytest.MonkeyPatch,
) -> None:
    api_client = AsyncMock()
    api_client.health.side_effect = [
        httpx.ConnectError("temporarily unavailable"),
        None,
    ]
    vector_store = AsyncMock()
    wait = AsyncMock(return_value=False)
    monkeypatch.setattr("ethnowear_indexer.app.wait_for_stop", wait)
    monkeypatch.setattr(
        "ethnowear_indexer.app.bounded_jitter",
        lambda delay: delay,
    )

    ready = await wait_for_dependencies(
        api_client=api_client,
        vector_store=vector_store,
        stop_event=asyncio.Event(),
        minimum_delay=1.0,
        maximum_delay=10.0,
    )

    assert ready is True
    assert api_client.health.await_count == 2
    wait.assert_awaited_once()
    assert wait.await_args.args[1] == 1.0


@pytest.mark.asyncio
async def test_dependencies_propagate_invalid_contract() -> None:
    api_client = AsyncMock()
    api_client.health.side_effect = WorkerApiContractError(
        "invalid readiness response"
    )

    with pytest.raises(WorkerApiContractError):
        await wait_for_dependencies(
            api_client=api_client,
            vector_store=AsyncMock(),
            stop_event=asyncio.Event(),
            minimum_delay=1.0,
            maximum_delay=10.0,
        )


@pytest.mark.asyncio
async def test_dependencies_stop_cleanly_during_backoff(
        monkeypatch: pytest.MonkeyPatch,
) -> None:
    api_client = AsyncMock()
    api_client.health.side_effect = RuntimeError("API unavailable")
    wait = AsyncMock(return_value=True)
    monkeypatch.setattr("ethnowear_indexer.app.wait_for_stop", wait)

    ready = await wait_for_dependencies(
        api_client=api_client,
        vector_store=AsyncMock(),
        stop_event=asyncio.Event(),
        minimum_delay=1.0,
        maximum_delay=10.0,
    )

    assert ready is False
