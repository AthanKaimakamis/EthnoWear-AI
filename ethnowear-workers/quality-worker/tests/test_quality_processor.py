import asyncio
import json
from pathlib import Path

import pytest
from PIL import Image

from ethnowear_quality_worker.api.models import (
    CompletionResponse,
    HeartbeatResponse,
    QualityAssessmentContextResponse,
    QualityAssessmentResponse,
    WorkerJobType,
)
from ethnowear_quality_worker.jobs.processor import QualityAssessmentProcessor
from ethnowear_quality_worker.api.models import ClaimResponse


def quality_claim():
    return ClaimResponse.model_validate({
        "jobId": 13,
        "jobType": "OCR_QUALITY_ASSESSMENT",
        "claimToken": "opaque-token",
        "attempt": 1,
        "claimedAt": "2026-08-22T10:00:00Z",
        "leaseExpiresAt": "2026-08-22T10:02:00Z",
        "target": {
            "documentId": 7,
            "documentPageId": 21,
            "knowledgeChunkId": None,
            "inputAvailable": True,
        },
        "limits": {
            "maximumInputBytes": 25_000_000,
            "maximumPageCount": 2_000,
            "renderDpi": 300,
            "maximumPixelWidth": 20_000,
            "maximumPixelHeight": 20_000,
            "maximumPagePixels": 100_000_000,
            "maximumRenditionBytes": 25_000_000,
            "maximumOcrTextCharacters": 1_000_000,
            "maximumOcrOutputBytes": 10_000_000,
            "maximumOcrContextBytes": 16_000_000,
            "maximumQualityAssessmentPayloadBytes": 1_000_000,
            "maximumQualitySignals": 100,
            "maximumQualitySignalTypeCharacters": 100,
            "maximumQualitySignalTextCharacters": 500,
            "maximumQualitySummaryCharacters": 1_000,
            "maximumQualityLimitationsCharacters": 1_000,
            "maximumQualityMessageCharacters": 1_000,
            "maximumVisionAssessmentPayloadBytes": 10_000_000,
            "maximumVisionSuggestionCharacters": 2_000_000,
            "maximumVisionIssuesJsonCharacters": 4_000,
            "maximumVisionIssues": 100,
            "maximumVisionUncertainPassages": 100,
            "maximumVisionIssueCodeCharacters": 100,
            "maximumVisionExcerptCharacters": 500,
            "maximumVisionReasonCharacters": 1_000,
            "maximumVisionModelNameCharacters": 100,
            "maximumVisionModelVersionCharacters": 100,
            "maximumVisionPromptVersionCharacters": 100,
            "maximumIndexingContentCharacters": 10_000,
            "maximumEmbeddingDimensions": 4_096,
            "maximumEmbeddingModelCharacters": 100,
            "maximumVectorCollectionCharacters": 150,
            "maximumVectorPointIdCharacters": 255,
            "jobTimeoutSeconds": 1_800,
            "heartbeatIntervalSeconds": 30,
            "maximumLeaseSeconds": 300,
        },
    })


def context() -> QualityAssessmentContextResponse:
    words = [
        {
            "page": 1,
            "block": 1,
            "paragraph": 1,
            "line": index // 10 + 1,
            "word": index + 1,
            "left": (index % 10) * 10,
            "top": (index // 10 + 1) * 20,
            "width": 8,
            "height": 10,
            "confidence": 0.92,
            "text": "шевица",
        }
        for index in range(30)
    ]
    return QualityAssessmentContextResponse(
        job_id=13,
        document_id=7,
        page_id=21,
        ocr_result_id=31,
        page_media_id=41,
        input_media_id=51,
        raw_ocr_text=(
            "Българската народна шевица съдържа традиционни орнаменти. " * 3
        ),
        ocr_confidence=0.92,
        ocr_language="bul",
        structured_output_json=json.dumps({
            "schemaVersion": 1,
            "words": words,
        }),
        image_mime_type="image/png",
        image_size_bytes=500,
        image_width=200,
        image_height=300,
        image_dpi=300,
        image_color_mode="GRAYSCALE",
    )


class FakeApiClient:
    def __init__(
            self,
            *,
            assessment_existing: bool = False,
            valid_image: bool = True,
            quality_context: QualityAssessmentContextResponse | None = None,
    ) -> None:
        self.assessment_existing = assessment_existing
        self.valid_image = valid_image
        self.quality_context = quality_context or context()
        self.calls: list[str] = []
        self.submitted = None
        self.failure = None

    async def get_quality_assessment_context(self, credentials):
        self.calls.append("context")
        return self.quality_context

    async def download_input(
            self,
            credentials,
            destination: Path,
            maximum_bytes: int,
    ) -> int:
        self.calls.append("download")
        if self.valid_image:
            Image.new("L", (200, 300), "white").save(destination, "PNG")
        else:
            destination.write_bytes(b"not-an-image")
        return destination.stat().st_size

    async def submit_quality_assessment(self, credentials, assessment):
        self.calls.append("submit")
        self.submitted = assessment
        return QualityAssessmentResponse(
            assessment_id=61,
            job_id=13,
            document_id=7,
            page_id=21,
            ocr_result_id=31,
            input_media_id=51,
            existing=self.assessment_existing,
        )

    async def complete(self, credentials):
        self.calls.append("complete")
        return CompletionResponse(
            job_id=13,
            job_type=WorkerJobType.OCR_QUALITY_ASSESSMENT,
            document_id=7,
            document_page_id=21,
            page_count=0,
            queued_ocr_jobs=0,
            queued_quality_assessment_jobs=0,
            queued_vision_assessment_jobs=0,
            existing=False,
        )

    async def fail(self, credentials, failure):
        self.calls.append("fail")
        self.failure = failure

    async def acknowledge_cancellation(self, credentials):
        self.calls.append("cancelled")

    async def heartbeat(self, credentials, lease_seconds=None):
        self.calls.append("heartbeat")
        return HeartbeatResponse(
            lease_expires_at="2026-08-22T10:04:00Z",
            cancellation_requested=False,
        )


def processor(api, temporary_root: Path) -> QualityAssessmentProcessor:
    return QualityAssessmentProcessor(
        api_client=api,
        temporary_root=temporary_root,
    )


def test_quality_processor_evaluates_submits_completes_and_cleans(
        tmp_path: Path,
) -> None:
    api = FakeApiClient()
    temporary_root = tmp_path / "jobs"

    asyncio.run(processor(api, temporary_root).process(quality_claim()))

    assert api.calls == ["context", "download", "submit", "complete"]
    assert api.submitted.assessor_name == "ethnowear-deterministic-quality"
    assert len(api.submitted.signals) == 18
    assert list(temporary_root.iterdir()) == []


def test_quality_processor_completes_idempotent_existing_assessment(
        tmp_path: Path,
) -> None:
    api = FakeApiClient(assessment_existing=True)

    asyncio.run(processor(api, tmp_path / "jobs").process(quality_claim()))

    assert api.calls == ["context", "download", "submit", "complete"]


def test_quality_processor_reports_context_identity_mismatch(
        tmp_path: Path,
) -> None:
    invalid_context = context()
    invalid_context.ocr_result_id = 99
    api = FakeApiClient(quality_context=invalid_context)

    asyncio.run(processor(api, tmp_path / "jobs").process(quality_claim()))

    assert api.calls == ["context", "download", "submit", "fail"]
    assert api.failure.error_code == "WORKER_API_CONTRACT_INVALID"


def test_quality_processor_reports_invalid_image_and_cleans(
        tmp_path: Path,
) -> None:
    api = FakeApiClient(valid_image=False)
    temporary_root = tmp_path / "jobs"

    asyncio.run(processor(api, temporary_root).process(quality_claim()))

    assert api.calls == ["context", "download", "fail"]
    assert api.failure.error_code == "QUALITY_INPUT_INVALID"
    assert list(temporary_root.iterdir()) == []


def test_quality_processor_rejects_non_quality_claim_before_side_effects(
        tmp_path: Path,
) -> None:
    api = FakeApiClient()
    claim = quality_claim()
    claim.job_type = WorkerJobType.OCR

    with pytest.raises(ValueError, match="requires an OCR quality-assessment job"):
        asyncio.run(processor(api, tmp_path / "jobs").process(claim))

    assert api.calls == []
