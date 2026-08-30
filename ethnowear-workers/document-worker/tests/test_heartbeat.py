import asyncio
from datetime import UTC, datetime

import httpx
import pytest
from pydantic import SecretStr

from ethnowear_document_worker.api.models import HeartbeatResponse
from ethnowear_worker_common.jobs.context import ClaimCredentials
from ethnowear_worker_common.jobs.heartbeat import HeartbeatSupervisor


class FakeHeartbeatClient:
    def __init__(self, cancellation_results: list[bool]) -> None:
        self._results = iter(cancellation_results)
        self.calls = 0

    async def heartbeat(
        self,
        credentials: ClaimCredentials,
        lease_seconds: int | None = None,
    ) -> HeartbeatResponse:
        self.calls += 1
        return HeartbeatResponse(
            lease_expires_at=datetime(2026, 8, 22, 10, 4, tzinfo=UTC),
            cancellation_requested=next(self._results),
        )


def credentials() -> ClaimCredentials:
    return ClaimCredentials(11, SecretStr("opaque-claim-token"))


def test_heartbeat_runs_at_interval_until_cancellation_is_requested() -> None:
    client = FakeHeartbeatClient([False, False, True])
    delays: list[float] = []

    async def wait_for_stop(stop_event: asyncio.Event, delay: float) -> bool:
        delays.append(delay)
        return False

    supervisor = HeartbeatSupervisor(
        api_client=client,
        credentials=credentials(),
        interval_seconds=30,
        wait_for_stop=wait_for_stop,
    )
    stop_event = asyncio.Event()
    cancellation_event = asyncio.Event()

    asyncio.run(supervisor.run(stop_event, cancellation_event))

    assert client.calls == 3
    assert delays == [30, 30, 30]
    assert cancellation_event.is_set()


def test_heartbeat_stops_without_call_when_shutdown_interrupts_wait() -> None:
    client = FakeHeartbeatClient([])

    async def interrupted(stop_event: asyncio.Event, delay: float) -> bool:
        stop_event.set()
        return True

    supervisor = HeartbeatSupervisor(
        api_client=client,
        credentials=credentials(),
        interval_seconds=30,
        wait_for_stop=interrupted,
    )

    asyncio.run(
        supervisor.run(asyncio.Event(), asyncio.Event())
    )

    assert client.calls == 0


def test_heartbeat_rejects_non_positive_interval() -> None:
    try:
        HeartbeatSupervisor(
            api_client=FakeHeartbeatClient([]),
            credentials=credentials(),
            interval_seconds=0,
        )
    except ValueError:
        pass
    else:
        raise AssertionError("Invalid heartbeat interval was accepted")


def test_heartbeat_propagates_api_transport_failure() -> None:
    class FailingHeartbeatClient:
        async def heartbeat(self, credentials, lease_seconds=None):
            raise httpx.ConnectError("backend unavailable")

    async def elapsed(stop_event: asyncio.Event, delay: float) -> bool:
        return False

    supervisor = HeartbeatSupervisor(
        api_client=FailingHeartbeatClient(),
        credentials=credentials(),
        interval_seconds=30,
        wait_for_stop=elapsed,
    )

    with pytest.raises(httpx.ConnectError):
        asyncio.run(supervisor.run(asyncio.Event(), asyncio.Event()))
