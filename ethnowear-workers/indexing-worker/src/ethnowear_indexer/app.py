import asyncio
import logging
import signal

import httpx
from qdrant_client import AsyncQdrantClient
from qdrant_client.http.exceptions import (
    ResponseHandlingException,
    UnexpectedResponse
)

from ethnowear_indexer.api.client import IndexerApiClient
from ethnowear_indexer.config import IndexerSettings
from ethnowear_indexer.jobs.processor import (
    IndexChunkOperation,
    IndexChunkProcessor,
)
from ethnowear_indexer.vector.qdrant import QdrantVectorStore
from ethnowear_worker_common.api.errors import (
    WorkerApiContractError,
    WorkerApiRequestError,
)
from ethnowear_worker_common.health import (
    mark_not_ready,
    mark_ready
)
from ethnowear_worker_common.jobs.heartbeat import wait_for_stop
from ethnowear_worker_common.jobs.poller import (
    JobPoller,
    bounded_jitter
)
from ethnowear_worker_common.jobs.runner import WorkerRunner

logger = logging.getLogger(__name__)


async def run_worker(settings: IndexerSettings) -> None:
    mark_not_ready(settings.temporary_root)

    stop_event = asyncio.Event()
    install_signal_handlers(stop_event)

    api_timeout = httpx.Timeout(
        settings.http_read_timeout_seconds,
        connect=settings.http_connect_timeout_seconds
    )

    connection_limits = httpx.Limits(
        max_connections=4,
        max_keepalive_connections=1
    )

    transport = httpx.AsyncHTTPTransport(retries=2)

    qdrant_client = AsyncQdrantClient(
        url=settings.qdrant_url,
        timeout=settings.qdrant_timeout_seconds
    )

    logger.info("worker_started", extra={"worker_id": settings.worker_id})

    try:
        async with httpx.AsyncClient(
                timeout=api_timeout,
                limits=connection_limits,
                transport=transport,
                follow_redirects=False,
            ) as api_http_client:
            api_client = IndexerApiClient(settings, api_http_client)
            vector_store = QdrantVectorStore(settings, qdrant_client)

            ready = await wait_for_dependencies(
                api_client=api_client,
                vector_store=vector_store,
                stop_event=stop_event,
                minimum_delay=settings.poll_min_seconds,
                maximum_delay=settings.poll_max_seconds,
            )

            if not ready:
                return

            operation = IndexChunkOperation(
                api_client=api_client,
                vector_store=vector_store,
                settings=settings,
            )

            processor = IndexChunkProcessor(
                api_client=api_client,
                operation=operation,
            )

            poller = JobPoller(
                api_client=api_client,
                minimum_delay=settings.poll_min_seconds,
                maximum_delay=settings.poll_max_seconds,
            )

            runner = WorkerRunner(
                poller=poller,
                processor=processor,
            )

            mark_ready(settings.temporary_root)
            await runner.run(stop_event)

    finally:
        mark_not_ready(settings.temporary_root)
        await qdrant_client.close()

        logger.info("worker_stopped", extra={"worker_id": settings.worker_id})


async def wait_for_dependencies(
        *,
        api_client: IndexerApiClient,
        vector_store: QdrantVectorStore,
        stop_event: asyncio.Event,
        minimum_delay: float,
        maximum_delay: float,
) -> bool:
    delay = minimum_delay

    while not stop_event.is_set():
        try:
            await api_client.health()
            await vector_store.health()
            return True

        except WorkerApiRequestError as error:
            if error.status_code != 429 and error.status_code < 500:
                raise

            logger.warning("worker_dependency_unavailable")

        except UnexpectedResponse as error:
            if error.status_code != 429 and error.status_code < 500:
                raise

            logger.warning("worker_dependency_unavailable")

        except WorkerApiContractError:
            raise

        except (httpx.TransportError,
                ResponseHandlingException,
                RuntimeError):
            logger.warning("worker_dependency_unavailable")

        interrupted = await wait_for_stop(
            stop_event,
            max(0.001, min(bounded_jitter(delay), maximum_delay))
        )

        if interrupted:
            return False

        delay = min(delay * 2, maximum_delay)

    return False


def install_signal_handlers(stop_event: asyncio.Event) -> None:
    loop = asyncio.get_running_loop()

    for shutdown_signal in (signal.SIGTERM, signal.SIGINT):
        try:
            loop.add_signal_handler(shutdown_signal, stop_event.set)
        except NotImplementedError:
            signal.signal(
                shutdown_signal,
                lambda *_: loop.call_soon_threadsafe(stop_event.set)
            )
