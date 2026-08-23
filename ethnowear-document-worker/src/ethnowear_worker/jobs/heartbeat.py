import asyncio
from collections.abc import Awaitable, Callable
from typing import Protocol

from ethnowear_worker.api.models import HeartbeatResponse
from ethnowear_worker.jobs.context import ClaimCredentials


class HeartbeatClient(Protocol):
    async def heartbeat(
            self,
            credentials: ClaimCredentials,
            lease_seconds: int | None = None,
    ) -> HeartbeatResponse:
        ...


WaitForStop = Callable[
    [asyncio.Event, float],
    Awaitable[bool],
]


async def wait_for_stop(stop_event: asyncio.Event, delay: float) -> bool:
    try:
        await asyncio.wait_for(stop_event.wait(), timeout=delay)
        return True
    except asyncio.TimeoutError:
        return False

class HeartbeatSupervisor:
    def __init__(
            self,
            api_client: HeartbeatClient,
            credentials: ClaimCredentials,
            interval_seconds: float,
            wait_for_stop: WaitForStop = wait_for_stop,
    ) -> None:
        if interval_seconds <= 0:
            raise ValueError("Heartbeat interval must be positive")

        self._api_client = api_client
        self._credentials = credentials
        self._interval_seconds = interval_seconds
        self._wait_for_stop = wait_for_stop

    async def run(
            self,
            stop_event: asyncio.Event,
            cancellation_event: asyncio.Event,
    ) -> None:
        while not stop_event.is_set():
            interrupted = await self._wait_for_stop(
                stop_event,
                self._interval_seconds,
            )

            if interrupted or stop_event.is_set():
                return

            response = await self._api_client.heartbeat(
                self._credentials
            )

            if response.cancellation_requested:
                cancellation_event.set()
                return