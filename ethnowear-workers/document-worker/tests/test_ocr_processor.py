import asyncio
from dataclasses import replace
import json
import logging
from pathlib import Path

import pytest
from PIL import Image

from ethnowear_worker_common.api.errors import WorkerInputError
from ethnowear_document_worker.api.models import (
    CompletionResponse,
    HeartbeatResponse,
    OcrResultResponse,
    WorkerJobType,
)
from ethnowear_document_worker.jobs.ocr_processor import OcrProcessor
from ethnowear_document_worker.ocr.models import OcrOutput, OcrWord, TsvParseDiagnostics
from ethnowear_document_worker.ocr.tsv import InvalidTsvError
from test_ocr_job import claim as make_claim


def recognized_output() -> OcrOutput:
    return OcrOutput(
        raw_text="Българска шевица",
        mean_confidence=0.91,
        words=(OcrWord(
            page_number=1,
            block_number=1,
            paragraph_number=1,
            line_number=1,
            word_number=1,
            left=10,
            top=20,
            width=30,
            height=40,
            confidence=0.91,
            text="шевица",
        ),),
        engine_name="tesseract",
        engine_version="5.5.0",
        language="bul",
    )


class FakeTesseract:
    language = "bul"

    def __init__(
        self,
        result: OcrOutput | None = None,
        rotation: int = 0,
    ) -> None:
        self.result = result or recognized_output()
        self.rotation = rotation
        self.rotation_calls: list[Path] = []
        self.calls: list[tuple[Path, int, int]] = []

    async def engine_version(self) -> str:
        return "5.5.0"

    async def detect_rotation(self, image_path: Path) -> int:
        assert image_path.exists()
        self.rotation_calls.append(image_path)
        return self.rotation

    async def recognize(
        self,
        image_path: Path,
        maximum_output_bytes: int,
        *,
        psm: int | None = None,
        offset_x: int = 0,
        offset_y: int = 0,
    ):
        assert image_path.exists()
        self.calls.append((image_path, maximum_output_bytes, psm or 3))
        return self.result


class FakeApiClient:
    def __init__(
        self,
        *,
        existing: bool = False,
        cancel_on_heartbeat: bool = False,
        valid_image: bool = True,
    ) -> None:
        self.existing = existing
        self.cancel_on_heartbeat = cancel_on_heartbeat
        self.valid_image = valid_image
        self.calls: list[str] = []
        self.submitted = None
        self.failure = None

    async def download_ocr_input(
        self,
        credentials,
        destination: Path,
        maximum_bytes: int,
    ) -> int:
        self.calls.append("download")
        if self.valid_image:
            Image.new("RGB", (20, 10), "white").save(destination, "PNG")
        else:
            destination.write_bytes(b"not-an-image")
        return destination.stat().st_size

    async def submit_ocr_result(self, credentials, request):
        self.calls.append("submit")
        self.submitted = request
        return OcrResultResponse(
            ocr_result_id=31,
            job_id=12,
            document_id=7,
            page_id=21,
            input_media_id=41,
            existing=self.existing,
        )

    async def complete(self, credentials) -> CompletionResponse:
        self.calls.append("complete")
        return CompletionResponse(
            job_id=12,
            job_type=WorkerJobType.OCR,
            document_id=7,
            document_page_id=21,
            page_count=0,
            queued_ocr_jobs=0,
            queued_quality_assessment_jobs=1,
            queued_vision_assessment_jobs=0,
            existing=False,
        )

    async def fail(self, credentials, failure) -> None:
        self.calls.append("fail")
        self.failure = failure

    async def acknowledge_cancellation(self, credentials) -> None:
        self.calls.append("cancelled")

    async def heartbeat(self, credentials, lease_seconds=None):
        self.calls.append("heartbeat")
        return HeartbeatResponse(
            lease_expires_at="2026-08-22T10:04:00Z",
            cancellation_requested=self.cancel_on_heartbeat,
        )


def processor(api, temporary_root: Path, tesseract=None) -> OcrProcessor:
    return OcrProcessor(
        api_client=api,
        temporary_root=temporary_root,
        tesseract=tesseract or FakeTesseract(),
        oem=1,
        psm=3,
    )


def test_ocr_processor_downloads_inspects_recognizes_submits_and_cleans(
    tmp_path: Path,
) -> None:
    api = FakeApiClient()
    tesseract = FakeTesseract()
    temporary_root = tmp_path / "jobs"

    asyncio.run(
        processor(api, temporary_root, tesseract).process(make_claim())
    )

    assert api.calls == ["download", "submit", "complete"]
    assert api.submitted.raw_text == "Българска шевица"
    assert api.submitted.ocr_language == "bul"
    assert json.loads(api.submitted.parameters_json)["preprocessed"] is False
    assert len(tesseract.rotation_calls) == 1
    assert {call[2] for call in tesseract.calls} == {3, 4, 6}
    assert all(call[1] == 10_000_000 for call in tesseract.calls)
    assert list(temporary_root.iterdir()) == []


def test_ocr_processor_corrects_rotation_before_recognition(
    tmp_path: Path,
) -> None:
    api = FakeApiClient()
    tesseract = FakeTesseract(rotation=90)

    asyncio.run(
        processor(api, tmp_path / "jobs", tesseract).process(make_claim())
    )

    assert any(call[0].name == "masked.png" for call in tesseract.calls)
    assert json.loads(api.submitted.parameters_json)["preprocessed"] is True
    assert api.calls == ["download", "submit", "complete"]


def test_ocr_processor_accepts_empty_page(tmp_path: Path) -> None:
    empty = OcrOutput(
        raw_text="",
        mean_confidence=None,
        words=(),
        engine_name="tesseract",
        engine_version="5.5.0",
        language="bul",
    )
    api = FakeApiClient()

    asyncio.run(
        processor(api, tmp_path / "jobs", FakeTesseract(empty)).process(
            make_claim()
        )
    )

    assert api.submitted.raw_text == ""
    assert api.submitted.ocr_confidence is None
    assert api.calls[-1] == "complete"


def test_ocr_processor_completes_after_idempotent_existing_result(
    tmp_path: Path,
) -> None:
    api = FakeApiClient(existing=True)

    asyncio.run(processor(api, tmp_path / "jobs").process(make_claim()))

    assert api.calls == ["download", "submit", "complete"]


def test_ocr_processor_reports_invalid_image_and_cleans_workspace(
    tmp_path: Path,
) -> None:
    api = FakeApiClient(valid_image=False)
    temporary_root = tmp_path / "jobs"

    asyncio.run(processor(api, temporary_root).process(make_claim()))

    assert api.calls == ["download", "fail"]
    assert api.failure.error_code == "OCR_INPUT_INVALID"
    assert api.failure.safe_error_message == (
        "The OCR page image could not be accepted"
    )
    assert list(temporary_root.iterdir()) == []


def test_ocr_processor_maps_download_validation_to_ocr_input_failure(
    tmp_path: Path,
) -> None:
    class InvalidDownloadApiClient(FakeApiClient):
        async def download_ocr_input(
            self,
            credentials,
            destination: Path,
            maximum_bytes: int,
        ) -> int:
            self.calls.append("download")
            raise WorkerInputError(
                "/private/tmp/secret-image has an invalid signature"
            )

    api = InvalidDownloadApiClient()
    temporary_root = tmp_path / "jobs"

    asyncio.run(processor(api, temporary_root).process(make_claim()))

    assert api.calls == ["download", "fail"]
    assert api.failure.error_code == "OCR_INPUT_INVALID"
    assert "/private/tmp" not in api.failure.safe_error_message
    assert list(temporary_root.iterdir()) == []


def test_ocr_processor_cancels_running_tesseract_and_cleans_workspace(
    tmp_path: Path,
) -> None:
    class BlockingTesseract(FakeTesseract):
        def __init__(self) -> None:
            super().__init__()
            self.cancelled = False

        async def recognize(self, image_path: Path, maximum_output_bytes: int, **kwargs):
            try:
                await asyncio.Event().wait()
            finally:
                self.cancelled = True

    api = FakeApiClient(cancel_on_heartbeat=True)
    tesseract = BlockingTesseract()
    job = make_claim()
    job.limits.heartbeat_interval_seconds = 0.01
    temporary_root = tmp_path / "jobs"

    asyncio.run(processor(api, temporary_root, tesseract).process(job))

    assert tesseract.cancelled is True
    assert api.calls[-1] == "cancelled"
    assert "submit" not in api.calls
    assert "complete" not in api.calls
    assert list(temporary_root.iterdir()) == []


def test_ocr_processor_rejects_non_ocr_claim_before_side_effects(
    tmp_path: Path,
) -> None:
    job = make_claim()
    job.job_type = WorkerJobType.PAGE_EXTRACTION
    api = FakeApiClient()

    with pytest.raises(ValueError, match="requires an OCR job"):
        asyncio.run(processor(api, tmp_path / "jobs").process(job))

    assert api.calls == []


def test_ocr_processor_logs_safe_tsv_failure_diagnostics(
    tmp_path: Path,
    caplog,
) -> None:
    secret = "raw-OCR-/private/tmp/secret-page.png"

    class InvalidTsvTesseract(FakeTesseract):
        async def recognize(self, *args, **kwargs):
            raise InvalidTsvError(
                secret,
                category="no_usable_words",
                rejected_row_count=4,
                usable_word_count=0,
                selected_psm=kwargs.get("psm"),
            )

    api = FakeApiClient()
    with caplog.at_level(
        logging.ERROR,
        logger="ethnowear_document_worker.jobs.ocr_processor",
    ):
        asyncio.run(
            processor(api, tmp_path / "jobs", InvalidTsvTesseract()).process(
                make_claim()
            )
        )

    assert api.calls == ["download", "fail"]
    assert api.failure.error_code == "OCR_OUTPUT_INVALID"
    record = next(record for record in caplog.records if record.message == "ocr_tsv_rejected")
    assert record.job_id == 12
    assert record.document_page_id == 21
    assert record.failure_category == "fallback_exhausted"
    assert record.rejected_row_count == 4
    assert record.usable_word_count == 0
    assert record.selected_psm == 6
    assert secret not in caplog.text
    assert "/private/tmp" not in caplog.text


def test_ocr_processor_logs_isolated_rejected_rows_without_raw_text(
    tmp_path: Path,
    caplog,
) -> None:
    diagnostic_output = replace(
        recognized_output(),
        tsv_diagnostics=TsvParseDiagnostics(
        total_row_count=40,
        rejected_row_count=1,
        usable_word_count=30,
            rejection_reasons=(("numeric_value", 1),),
        ),
    )
    api = FakeApiClient()

    with caplog.at_level(
        logging.WARNING,
        logger="ethnowear_document_worker.jobs.ocr_processor",
    ):
        asyncio.run(
            processor(api, tmp_path / "jobs", FakeTesseract(diagnostic_output)).process(
                make_claim()
            )
        )

    record = next(record for record in caplog.records if record.message == "ocr_tsv_rows_skipped")
    assert record.job_id == 12
    assert record.document_page_id == 21
    assert record.rejected_row_count == 1
    assert record.rejection_reasons == {"numeric_value": 1}
    assert "Българска шевица" not in caplog.text
