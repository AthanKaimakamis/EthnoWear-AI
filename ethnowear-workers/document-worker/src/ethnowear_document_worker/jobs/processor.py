import asyncio
import logging
import time
from pathlib import Path

from ethnowear_document_worker.api.client import WorkerApiClient
from ethnowear_worker_common.api.models import ClaimResponse
from ethnowear_worker_common.jobs.context import ClaimCredentials
from ethnowear_worker_common.jobs.heartbeat import HeartbeatSupervisor
from ethnowear_document_worker.jobs.page_extraction import (
    build_manifest,
    build_rendition_metadata,
    select_rendition_pages,
    validate_manifest_response,
)
from ethnowear_document_worker.pdf.inspector import inspect_pdf
from ethnowear_document_worker.pdf.renderer import render_page
from ethnowear_document_worker.temp.workspace import JobWorkspace
from ethnowear_document_worker.jobs.failures import classify_failure


class JobCancellationRequested(RuntimeError):
    pass


logger = logging.getLogger(__name__)


class PageExtractionProcessor:
    def __init__(self, api_client: WorkerApiClient, temporary_root: Path) -> None:
        self._api_client = api_client
        self._temporary_root = temporary_root

    async def process(self, claim: ClaimResponse) -> None:
        if claim.target.document_id is None or not claim.target.input_available:
            raise ValueError("PAGE_EXTRACTION claim has no PDF input")

        started_at = time.monotonic()
        processing_stage = "claim_validation"

        logger.info(
            "job_started",
            extra={
                "job_id": claim.job_id,
                "attempt": claim.attempt,
            }
        )

        credentials = ClaimCredentials.from_claim(claim)
        stop_heartbeat = asyncio.Event()
        cancellation_requested = asyncio.Event()

        heartbeat = HeartbeatSupervisor(
            api_client=self._api_client,
            credentials=credentials,
            interval_seconds=claim.limits.heartbeat_interval_seconds,
        )

        heartbeat_task = asyncio.create_task(
            heartbeat.run(stop_heartbeat, cancellation_requested)
        )

        try:
            try:
                async with asyncio.timeout(
                        claim.limits.job_timeout_seconds
                ):
                    with JobWorkspace.create(
                            self._temporary_root,
                            claim.job_id,
                            claim.attempt,
                    ) as workspace:
                        processing_stage = "pdf_download"
                        await self._api_client.download_input(
                            credentials,
                            workspace.input_pdf,
                            claim.limits.maximum_input_bytes,
                        )

                        self._raise_if_cancelled(cancellation_requested)
                        self._raise_if_heartbeat_failed(heartbeat_task)

                        processing_stage = "pdf_inspection"
                        inspection = await asyncio.to_thread(
                            inspect_pdf,
                            path=workspace.input_pdf,
                            maximum_page_count=claim.limits.maximum_page_count,
                            render_dpi=claim.limits.render_dpi,
                            maximum_pixel_width=claim.limits.maximum_pixel_width,
                            maximum_pixel_height=claim.limits.maximum_pixel_height,
                            maximum_page_pixels=claim.limits.maximum_page_pixels,
                        )

                        self._raise_if_heartbeat_failed(heartbeat_task)

                        processing_stage = "manifest_submission"
                        manifest = await self._api_client.submit_manifest(
                            credentials,
                            build_manifest(inspection),
                        )
                        processing_stage = "manifest_validation"
                        validate_manifest_response(
                            manifest,
                            claim.target.document_id,
                            inspection,
                        )

                        pages_by_index = {
                            page.pdf_page_index: page
                            for page in inspection.pages
                        }

                        rendition_pages = select_rendition_pages(
                            manifest,
                            target_page_id=claim.target.document_page_id,
                            target_pdf_page_index=claim.target.pdf_page_index,
                        )

                        for manifest_page in rendition_pages:
                            self._raise_if_cancelled(cancellation_requested)
                            self._raise_if_heartbeat_failed(heartbeat_task)

                            page_info = pages_by_index[
                                manifest_page.pdf_page_index
                            ]

                            processing_stage = "page_render"
                            rendered = await asyncio.to_thread(
                                render_page,
                                pdf_path=workspace.input_pdf,
                                page_info=page_info,
                                output_path=workspace.rendition_path(
                                    manifest_page.page_id,
                                    "jpg",
                                ),
                                render_dpi=claim.limits.render_dpi,
                                maximum_pixel_width=claim.limits.maximum_pixel_width,
                                maximum_pixel_height=claim.limits.maximum_pixel_height,
                                maximum_page_pixels=claim.limits.maximum_page_pixels,
                                maximum_bytes=claim.limits.maximum_rendition_bytes,
                            )

                            metadata = build_rendition_metadata(
                                manifest_page,
                                rendered,
                                claim.limits.render_dpi,
                            )

                            processing_stage = "rendition_upload"
                            await self._api_client.upload_rendition(
                                credentials,
                                manifest_page.page_id,
                                metadata,
                                rendered,
                            )

                            rendered.path.unlink(missing_ok=True)
                            self._raise_if_cancelled(cancellation_requested)
                            self._raise_if_heartbeat_failed(heartbeat_task)

                    self._raise_if_cancelled(cancellation_requested)
                    self._raise_if_heartbeat_failed(heartbeat_task)
                    processing_stage = "completion"
                    await self._api_client.complete(credentials)

                    logger.info(
                        "job_completed",
                        extra={
                            "job_id": claim.job_id,
                            "attempt": claim.attempt,
                            "duration_ms": int(
                                (time.monotonic() - started_at) * 1000
                            )
                        }
                    )

            except JobCancellationRequested:
                await self._api_client.acknowledge_cancellation(credentials)

                logger.info(
                    "job_cancelled",
                    extra={
                        "job_id": claim.job_id,
                        "attempt": claim.attempt,
                        "duration_ms": int(
                            (time.monotonic() - started_at) * 1000
                        ),
                    },
                )


            except Exception as error:
                failure = classify_failure(error)
                await self._api_client.fail(credentials, failure)

                logger.error(
                    "job_failed",
                    extra={
                        "job_id": claim.job_id,
                        "attempt": claim.attempt,
                        "error_code": failure.error_code,
                        "retryable": failure.retryable,
                        "processing_stage": processing_stage,
                        "exception_type": type(error).__name__,
                        "duration_ms": int(
                            (time.monotonic() - started_at) * 1000
                        ),
                    },
                )
        finally:
            stop_heartbeat.set()
            await asyncio.gather(
                heartbeat_task,
                return_exceptions=True,
            )

    @staticmethod
    def _raise_if_heartbeat_failed(heartbeat_task: asyncio.Task[None]) -> None:
        if not heartbeat_task.done():
            return

        if heartbeat_task.cancelled():
            raise RuntimeError("Heartbeat task stopped unexpectedly")

        error = heartbeat_task.exception()
        if error is not None:
            raise error

    @staticmethod
    def _raise_if_cancelled(cancellation_requested: asyncio.Event) -> None:
        if cancellation_requested.is_set():
            raise JobCancellationRequested("Job cancellation was requested")
