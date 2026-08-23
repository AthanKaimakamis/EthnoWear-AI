import asyncio
import logging
from datetime import UTC, datetime, timedelta
from pathlib import Path

import httpx
import pymupdf
import pytest
from pydantic import SecretStr

from ethnowear_worker.api.errors import WorkerInputError
from ethnowear_worker.api.models import (
    ClaimResponse,
    CancellationResponse,
    CompletionResponse,
    FailureResponse,
    HeartbeatResponse,
    JobTarget,
    JobStatus,
    ManifestResponse,
    ManifestPageResponse,
    RenditionResponse,
    RenditionType,
    ResourceLimits,
    WorkerJobType,
)
from ethnowear_worker.jobs.processor import PageExtractionProcessor


def make_claim() -> ClaimResponse:
    now = datetime.now(UTC)
    return ClaimResponse(
        job_id=11,
        job_type=WorkerJobType.PAGE_EXTRACTION,
        claim_token=SecretStr("opaque-claim-token"),
        attempt=1,
        claimed_at=now,
        lease_expires_at=now + timedelta(seconds=60),
        target=JobTarget(
            document_id=22,
            document_page_id=None,
            knowledge_chunk_id=None,
            input_available=True,
        ),
        limits=ResourceLimits(
            maximum_input_bytes=1_000_000,
            maximum_page_count=10,
            render_dpi=72,
            maximum_pixel_width=1_000,
            maximum_pixel_height=1_000,
            maximum_page_pixels=1_000_000,
            maximum_rendition_bytes=1_000_000,
            job_timeout_seconds=60,
            heartbeat_interval_seconds=60,
            maximum_lease_seconds=120,
        ),
    )


class FakeApiClient:
    def __init__(self, rendition_required: bool = True) -> None:
        self.rendition_required = rendition_required
        self.calls: list[str] = []
        self.reported_failure = None

    async def heartbeat(self, credentials, lease_seconds=None):
        self.calls.append("heartbeat")
        return HeartbeatResponse(
            lease_expires_at=datetime.now(UTC) + timedelta(seconds=60),
            cancellation_requested=False,
        )

    async def download_input(self, credentials, destination, maximum_bytes):
        self.calls.append("download")
        document = pymupdf.open()
        document.new_page(width=100, height=120)
        document.save(destination)
        document.close()
        return destination.stat().st_size

    async def submit_manifest(self, credentials, manifest):
        self.calls.append("manifest")
        return ManifestResponse(
            document_id=22,
            page_count=1,
            pages=(ManifestPageResponse(
                page_id=33,
                pdf_page_index=0,
                page_sequence=1,
                rendition_required=self.rendition_required,
            ),),
        )

    async def upload_rendition(self, credentials, page_id, metadata, rendered):
        self.calls.append("upload")
        assert page_id == 33
        assert rendered.path.exists()
        return RenditionResponse(
            page_id=33,
            page_media_id=44,
            media_asset_id=55,
            rendition_type=RenditionType.PDF_PAGE_RENDER,
            existing=False,
        )

    async def complete(self, credentials):
        self.calls.append("complete")
        return CompletionResponse(
            job_id=11,
            document_id=22,
            page_count=1,
            queued_ocr_jobs=1,
            existing=False,
        )

    async def acknowledge_cancellation(self, credentials):
        self.calls.append("cancelled")
        return CancellationResponse(
            job_id=11,
            status=JobStatus.CANCELLED,
            finished_at=datetime.now(UTC),
            existing=False,
        )

    async def fail(self, credentials, failure):
        self.calls.append("fail")
        self.reported_failure = failure
        return FailureResponse(
            job_id=11,
            status=JobStatus.FAILED,
            available_at=None,
            existing=False,
        )


def test_process_runs_page_extraction_and_cleans_workspace(
    tmp_path: Path,
    caplog,
) -> None:
    api_client = FakeApiClient()
    temporary_root = tmp_path / "jobs"
    caplog.set_level(logging.INFO)

    asyncio.run(
        PageExtractionProcessor(api_client, temporary_root).process(make_claim())
    )

    assert api_client.calls == ["download", "manifest", "upload", "complete"]
    assert list(temporary_root.iterdir()) == []
    assert [
        record.getMessage()
        for record in caplog.records
        if record.getMessage().startswith("job_")
    ] == ["job_started", "job_completed"]


def test_process_skips_existing_rendition(tmp_path: Path) -> None:
    api_client = FakeApiClient(rendition_required=False)

    asyncio.run(
        PageExtractionProcessor(api_client, tmp_path / "jobs").process(make_claim())
    )

    assert api_client.calls == ["download", "manifest", "complete"]


def test_process_acknowledges_heartbeat_cancellation(
    tmp_path: Path,
    monkeypatch,
    caplog,
) -> None:
    class CancellingHeartbeat:
        def __init__(self, **kwargs) -> None:
            pass

        async def run(self, stop_event, cancellation_event) -> None:
            cancellation_event.set()

    class YieldingApiClient(FakeApiClient):
        async def download_input(self, credentials, destination, maximum_bytes):
            result = await super().download_input(
                credentials, destination, maximum_bytes
            )
            await asyncio.sleep(0)
            return result

    monkeypatch.setattr(
        "ethnowear_worker.jobs.processor.HeartbeatSupervisor",
        CancellingHeartbeat,
    )
    api_client = YieldingApiClient()
    temporary_root = tmp_path / "jobs"
    caplog.set_level(logging.INFO)

    asyncio.run(
        PageExtractionProcessor(api_client, temporary_root).process(make_claim())
    )

    assert api_client.calls == ["download", "cancelled"]
    assert list(temporary_root.iterdir()) == []
    assert [
        record.getMessage()
        for record in caplog.records
        if record.getMessage() in {"job_completed", "job_cancelled", "job_failed"}
    ] == ["job_cancelled"]


def test_process_reports_sanitized_failure_and_does_not_complete(
    tmp_path: Path,
    caplog,
) -> None:
    secret = "opaque-token-and-/private/document.pdf"

    class FailingApiClient(FakeApiClient):
        async def download_input(self, credentials, destination, maximum_bytes):
            self.calls.append("download")
            raise WorkerInputError(secret)

    api_client = FailingApiClient()
    temporary_root = tmp_path / "jobs"
    caplog.set_level(logging.INFO)

    asyncio.run(
        PageExtractionProcessor(api_client, temporary_root).process(make_claim())
    )

    assert api_client.calls == ["download", "fail"]
    assert api_client.reported_failure.error_code == "PDF_INPUT_INVALID"
    assert api_client.reported_failure.retryable is False
    assert secret not in api_client.reported_failure.model_dump_json()
    assert list(temporary_root.iterdir()) == []
    terminal_records = [
        record
        for record in caplog.records
        if record.getMessage() in {"job_completed", "job_cancelled", "job_failed"}
    ]
    assert [record.getMessage() for record in terminal_records] == ["job_failed"]
    assert terminal_records[0].error_code == "PDF_INPUT_INVALID"
    assert terminal_records[0].retryable is False
    assert secret not in terminal_records[0].getMessage()


def test_process_reports_retryable_job_timeout(tmp_path: Path) -> None:
    class SlowApiClient(FakeApiClient):
        async def download_input(self, credentials, destination, maximum_bytes):
            self.calls.append("download")
            await asyncio.sleep(0.02)

    claim = make_claim()
    limits = claim.limits.model_copy(update={"job_timeout_seconds": 0.001})
    claim = claim.model_copy(update={"limits": limits})
    api_client = SlowApiClient()
    temporary_root = tmp_path / "jobs"

    asyncio.run(
        PageExtractionProcessor(api_client, temporary_root).process(claim)
    )

    assert api_client.calls == ["download", "fail"]
    assert api_client.reported_failure.error_code == "JOB_TIMEOUT"
    assert api_client.reported_failure.retryable is True
    assert list(temporary_root.iterdir()) == []


def test_process_propagates_failure_reporting_transport_error(
    tmp_path: Path,
) -> None:
    class FailureReportingApiClient(FakeApiClient):
        async def download_input(self, credentials, destination, maximum_bytes):
            self.calls.append("download")
            raise WorkerInputError("unsafe input detail")

        async def fail(self, credentials, failure):
            self.calls.append("fail")
            raise httpx.ConnectError("backend unavailable")

    api_client = FailureReportingApiClient()
    temporary_root = tmp_path / "jobs"

    with pytest.raises(httpx.ConnectError):
        asyncio.run(
            PageExtractionProcessor(api_client, temporary_root).process(make_claim())
        )

    assert api_client.calls == ["download", "fail"]
    assert list(temporary_root.iterdir()) == []
