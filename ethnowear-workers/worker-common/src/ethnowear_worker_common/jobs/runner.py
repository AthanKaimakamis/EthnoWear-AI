import asyncio
import logging
from typing import Protocol

import httpx

from ethnowear_worker_common.api.errors import WorkerApiRequestError
from ethnowear_worker_common.api.models import ClaimResponse
from ethnowear_worker_common.jobs.poller import JobPoller


class JobProcessor(Protocol):
    async def process(self, claim: ClaimResponse) -> None:
        ...


logger = logging.getLogger(__name__)


class WorkerRunner:
    def __init__(self, poller: JobPoller, processor: JobProcessor) -> None:
        self._poller = poller
        self._processor = processor

    async def run(self, stop_event: asyncio.Event) -> None:
        while not stop_event.is_set():
            claim = await self._poller.wait_for_job(stop_event)

            if claim is None:
                return

            try:
                await self._processor.process(claim)
            except httpx.TransportError:
                logger.warning("worker_api_unavailable")
            except WorkerApiRequestError as error:
                if error.status_code not in {409, 410, 429} and error.status_code < 500:
                    raise
                logger.warning("worker_api_unavailable")
