import asyncio
import logging
import signal
from typing import cast

import httpx

from ethnowear_worker_common.api.errors import WorkerApiRequestError
from ethnowear_worker_common.health import mark_not_ready, mark_ready
from ethnowear_worker_common.jobs.heartbeat import wait_for_stop
from ethnowear_worker_common.jobs.poller import (
    ClaimingClient,
    JobPoller,
    bounded_jitter,
)
from ethnowear_worker_common.jobs.runner import (
    JobProcessor,
    WorkerRunner,
)
from ethnowear_worker_common.temp import cleanup_stale_workspaces

from ethnowear_figure_worker.api.client import FigureWorkerApiClient
from ethnowear_figure_worker.config import FigureWorkerSettings
from ethnowear_figure_worker.jobs.processor import (
    FigureExtractionProcessor,
)

logger = logging.getLogger(__name__)


async def run_worker(settings: FigureWorkerSettings) -> None:
    mark_not_ready(settings.temporary_root)

    removed = cleanup_stale_workspaces(settings.temporary_root)
    if removed:
        logger.info(
            "stale_workspaces_removed",
            extra={"removed_count": removed})

    stop_event = asyncio.Event()
    install_signal_handlers(stop_event)

    timeout = httpx.Timeout(
        settings.http_read_timeout_seconds,
        connect=settings.http_connect_timeout_seconds
    )
    limits = httpx.Limits(
        max_connections=2,
        max_keepalive_connections=2,
    )

    logger.info(
        "worker_started",
        extra={"worker_id": settings.worker_id},
    )

    try:
        async with httpx.AsyncClient(
                timeout=timeout,
                limits=limits,
                transport=httpx.AsyncHTTPTransport(retries=2),
                follow_redirects=False,
        ) as http_client:
            api_client = FigureWorkerApiClient(settings, http_client)

            ready = await wait_for_api_readiness(
                api_client,
                stop_event,
                settings.poll_min_seconds,
                settings.poll_max_seconds,
            )
            if not ready:
                return

            processor = FigureExtractionProcessor(
                api_client=api_client,
                settings=settings,
            )
            runner = WorkerRunner(
                poller=JobPoller(
                    api_client=cast(ClaimingClient, api_client),
                    minimum_delay=settings.poll_min_seconds,
                    maximum_delay=settings.poll_max_seconds,
                ),
                processor=cast(JobProcessor, processor),
            )

            mark_ready(settings.temporary_root)
            await runner.run(stop_event)

    finally:
        mark_not_ready(settings.temporary_root)
        logger.info(
            "worker_stopped",
            extra={"worker_id": settings.worker_id},
        )


async def wait_for_api_readiness(
        api_client: FigureWorkerApiClient,
        stop_event: asyncio.Event,
        minimum_delay: float,
        maximum_delay: float,
) -> bool:
    delay = minimum_delay

    while not stop_event.is_set():
        try:
            await api_client.health()
            return True

        except httpx.TransportError:
            logger.warning("worker_api_unavailable")

        except WorkerApiRequestError as error:
            if error.status_code != 429 and error.status_code < 500:
                raise

            logger.warning("worker_api_unavailable")

        interrupted = await wait_for_stop(
            stop_event,
            max(
                0.001,
                min(
                    bounded_jitter(delay),
                    maximum_delay,
                ),
            ),
        )
        if interrupted:
            return False

        delay = min(delay * 2, maximum_delay)

    return False


def install_signal_handlers(stop_event: asyncio.Event) -> None:
    loop = asyncio.get_running_loop()

    for shutdown_signal in (
            signal.SIGTERM,
            signal.SIGINT,
    ):
        try:
            loop.add_signal_handler(
                shutdown_signal,
                stop_event.set,
            )
        except NotImplementedError:
            signal.signal(
                shutdown_signal,
                lambda *_: loop.call_soon_threadsafe(stop_event.set),
            )
