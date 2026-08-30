import asyncio
import logging
import random
from collections.abc import Callable
from typing import Protocol

import httpx

from ethnowear_worker_common.api.errors import WorkerApiRequestError
from ethnowear_worker_common.api.models import ClaimResponse
from ethnowear_worker_common.jobs.heartbeat import WaitForStop, wait_for_stop


class ClaimingClient(Protocol):
    async def claim(self) -> ClaimResponse | None:
        ...


Jitter = Callable[[float], float]


def bounded_jitter(delay: float) -> float:
    return random.uniform(delay * 0.8, delay)


logger = logging.getLogger(__name__)


class JobPoller:
    def __init__(
        self,
        api_client: ClaimingClient,
        minimum_delay: float,
        maximum_delay: float,
        wait_for_stop: WaitForStop = wait_for_stop,
        jitter: Jitter = bounded_jitter,
    ) -> None:
        if minimum_delay <= 0:
            raise ValueError("Minimum polling delay must be positive")

        if maximum_delay < minimum_delay:
            raise ValueError(
                "Maximum polling delay must be greater than or equal to the minimum"
            )

        self._api_client = api_client
        self._minimum_delay = minimum_delay
        self._maximum_delay = maximum_delay
        self._wait_for_stop = wait_for_stop
        self._jitter = jitter

    async def wait_for_job(self, stop_event: asyncio.Event) -> ClaimResponse | None:
        delay = self._minimum_delay

        while not stop_event.is_set():
            try:
                job = await self._api_client.claim()
            except httpx.TransportError:
                logger.warning("worker_api_unavailable")
                job = None
            except WorkerApiRequestError as error:
                if error.status_code != 429 and error.status_code < 500:
                    raise
                logger.warning("worker_api_unavailable")
                job = None

            if job is not None:
                return job

            if stop_event.is_set():
                return None

            wait_delay = max(0.001, min(self._jitter(delay), self._maximum_delay))
            interrupted = await self._wait_for_stop(stop_event, wait_delay)

            if interrupted or stop_event.is_set():
                return None

            delay = min(delay * 2, self._maximum_delay)

        return None
