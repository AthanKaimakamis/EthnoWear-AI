import asyncio

import httpx
import pytest
from pydantic import SecretStr

from ethnowear_worker_common.api.models import HeartbeatResponse
from ethnowear_worker_common.jobs.context import ClaimCredentials
from ethnowear_worker_common.jobs.heartbeat import HeartbeatSupervisor
from ethnowear_worker_common.jobs.poller import JobPoller


def test_claim_credentials_reject_invalid_job_identity() -> None:
    with pytest.raises(ValueError, match="Job ID must be positive"):
        ClaimCredentials(job_id=0, claim_token=SecretStr("opaque"))


def test_heartbeat_observes_cancellation() -> None:
    class Client:
        async def heartbeat(self, credentials, lease_seconds=None):
            return HeartbeatResponse(
                lease_expires_at="2026-08-25T12:00:00Z",
                cancellation_requested=True,
            )

    async def immediate_wait(stop_event, delay):
        return False

    async def execute() -> bool:
        cancellation = asyncio.Event()
        supervisor = HeartbeatSupervisor(
            Client(),
            ClaimCredentials(1, SecretStr("opaque")),
            interval_seconds=1,
            wait_for_stop=immediate_wait,
        )
        await supervisor.run(asyncio.Event(), cancellation)
        return cancellation.is_set()

    assert asyncio.run(execute()) is True


def test_poller_recovers_from_transport_failure() -> None:
    class Client:
        calls = 0

        async def claim(self):
            self.calls += 1
            if self.calls == 1:
                raise httpx.ConnectError("unavailable")
            return None

    async def stop_after_retry(stop_event, delay):
        if client.calls >= 2:
            stop_event.set()
            return True
        return False

    async def execute():
        stop = asyncio.Event()
        poller = JobPoller(
            client,
            minimum_delay=1,
            maximum_delay=2,
            wait_for_stop=stop_after_retry,
            jitter=lambda delay: delay,
        )
        return await poller.wait_for_job(stop)

    client = Client()
    assert asyncio.run(execute()) is None
    assert client.calls == 2
