import asyncio
import logging
import signal

import httpx

from ethnowear_document_worker.health import mark_not_ready, mark_ready
from ethnowear_document_worker.api.client import WorkerApiClient
from ethnowear_worker_common.api.errors import WorkerApiRequestError
from ethnowear_document_worker.config import WorkerSettings
from ethnowear_document_worker.jobs.processor import PageExtractionProcessor
from ethnowear_worker_common.jobs.heartbeat import wait_for_stop
from ethnowear_worker_common.jobs.poller import JobPoller, bounded_jitter
from ethnowear_worker_common.jobs.runner import WorkerRunner
from ethnowear_document_worker.temp.workspace import cleanup_stale_workspaces
from ethnowear_document_worker.jobs.dispatcher import JobDispatcher
from ethnowear_document_worker.jobs.ocr_processor import OcrProcessor
from ethnowear_document_worker.ocr.tesseract import TesseractRunner

logger = logging.getLogger(__name__)


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

            page_extraction_processor = PageExtractionProcessor(
                api_client=api_client,
                temporary_root=settings.temporary_root,
            )

            tesseract = TesseractRunner(
                binary=settings.tesseract_binary,
                language=settings.ocr_language,
                oem=settings.ocr_oem,
                psm=settings.ocr_psm,
                timeout_seconds=settings.ocr_timeout_seconds,
            )

            # Do not report ready or claim OCR work if the engine is unavailable.
            await tesseract.validate_runtime()

            ocr_processor = OcrProcessor(
                api_client=api_client,
                temporary_root=settings.temporary_root,
                tesseract=tesseract,
                oem=settings.ocr_oem,
                psm=settings.ocr_psm,
                maximum_regions=settings.ocr_maximum_layout_regions,
            )

            processor = JobDispatcher(
                page_extraction=page_extraction_processor,
                ocr=ocr_processor,
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
