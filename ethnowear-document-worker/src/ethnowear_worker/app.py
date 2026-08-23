import asyncio
import logging
import signal

import httpx

from ethnowear_worker.health import mark_not_ready, mark_ready
from ethnowear_worker.api.client import WorkerApiClient
from ethnowear_worker.api.errors import WorkerApiRequestError
from ethnowear_worker.config import WorkerSettings
from ethnowear_worker.jobs.processor import PageExtractionProcessor
from ethnowear_worker.jobs.heartbeat import wait_for_stop
from ethnowear_worker.jobs.poller import JobPoller, bounded_jitter
from ethnowear_worker.jobs.runner import WorkerRunner
from ethnowear_worker.logging import configure_logging
from ethnowear_worker.temp.workspace import cleanup_stale_workspaces

logger = logging.getLogger(__name__)


def main() -> int:
    configure_logging()

    try:
        settings = WorkerSettings.from_environment()
    except ValueError:
        logger.error("worker_configuration_invalid")
        return 2

    try:
        asyncio.run(run_worker(settings))
        return 0
    except KeyboardInterrupt:
        return 0
    except Exception:
        logger.error("worker_stopped_unexpectedly")
        return 1


async def run_worker(settings: WorkerSettings) -> None:
    mark_not_ready(settings.temporary_root)
    removed = cleanup_stale_workspaces(settings.temporary_root)
    if removed:
        logger.info("stale_workspaces_removed")
    stop_event = asyncio.Event()
    install_signal_handlers(stop_event)

    timeout = httpx.Timeout(
        settings.http_read_timeout_seconds,
        connect=settings.http_connect_timeout_seconds
    )

    limits = httpx.Limits(
        max_connections=1,
        max_keepalive_connections=1,
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
            api_client = WorkerApiClient(settings, http_client)
            ready = await wait_for_api_readiness(
                api_client,
                stop_event,
                settings.poll_min_seconds,
                settings.poll_max_seconds,
            )
            if not ready:
                return

            poller = JobPoller(
                api_client=api_client,
                minimum_delay=settings.poll_min_seconds,
                maximum_delay=settings.poll_max_seconds,
            )

            processor = PageExtractionProcessor(
                api_client=api_client,
                temporary_root=settings.temporary_root
            )

            runner = WorkerRunner(
                poller=poller,
                processor=processor,
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
        api_client: WorkerApiClient,
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
            max(0.001, min(bounded_jitter(delay), maximum_delay)),
        )
        if interrupted:
            return False
        delay = min(delay * 2, maximum_delay)

    return False


def install_signal_handlers(stop_event: asyncio.Event) -> None:
    loop = asyncio.get_running_loop()

    for shutdown_signal in (signal.SIGTERM, signal.SIGINT):
        try:
            loop.add_signal_handler(
                shutdown_signal,
                stop_event.set,
            )
        except NotImplementedError:
            signal.signal(
                shutdown_signal,
                lambda *_: loop.call_soon_threadsafe(stop_event.set))
