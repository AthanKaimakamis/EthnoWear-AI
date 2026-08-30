from datetime import UTC, datetime, timedelta

import pytest
from pydantic import ValidationError

from ethnowear_worker_common.api.models import (
    ClaimRequest,
    ClaimResponse,
    WorkerJobType,
)


def limits() -> dict[str, int]:
    return {
        "maximumInputBytes": 1,
        "maximumPageCount": 1,
        "renderDpi": 300,
        "maximumPixelWidth": 1,
        "maximumPixelHeight": 1,
        "maximumPagePixels": 1,
        "maximumRenditionBytes": 1,
        "maximumOcrTextCharacters": 1,
        "maximumOcrOutputBytes": 1,
        "maximumOcrContextBytes": 1,
        "maximumQualityAssessmentPayloadBytes": 1,
        "maximumQualitySignals": 1,
        "maximumQualitySignalTypeCharacters": 1,
        "maximumQualitySignalTextCharacters": 1,
        "maximumQualitySummaryCharacters": 1,
        "maximumQualityLimitationsCharacters": 1,
        "maximumQualityMessageCharacters": 1,
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
        "maximumFigureCandidates": 100,
        "maximumFigureCaptionCharacters": 2_000,
        "maximumPrintedFigureNumberCharacters": 100,
        "maximumFigureCropBytes": 25 * 1024 * 1024,
        "jobTimeoutSeconds": 1_800,
        "heartbeatIntervalSeconds": 30,
        "maximumLeaseSeconds": 300,
    }


def claim_payload(job_type: WorkerJobType) -> dict[str, object]:
    claimed_at = datetime.now(UTC)
    target = {
        "documentId": 10,
        "documentPageId": None,
        "pdfPageIndex": None,
        "knowledgeChunkId": None,
        "inputAvailable": True,
    }

    if job_type == WorkerJobType.INDEX_CHUNK:
        target = {
            "documentId": 10,
            "documentPageId": None,
            "pdfPageIndex": None,
            "knowledgeChunkId": 99,
            "inputAvailable": False,
        }

    return {
        "jobId": 20,
        "jobType": job_type.value,
        "claimToken": "opaque",
        "attempt": 1,
        "claimedAt": claimed_at.isoformat(),
        "leaseExpiresAt": (claimed_at + timedelta(minutes=2)).isoformat(),
        "target": target,
        "limits": limits(),
    }


def test_claim_request_requires_an_explicit_supported_job_type() -> None:
    with pytest.raises(ValidationError):
        ClaimRequest(worker_id="worker-1", supported_job_types=())


def test_index_claim_accepts_a_knowledge_chunk_target() -> None:
    claim = ClaimResponse.model_validate(
        claim_payload(WorkerJobType.INDEX_CHUNK)
    )

    assert claim.target.knowledge_chunk_id == 99
    assert claim.claim_token.get_secret_value() == "opaque"


def test_index_claim_rejects_a_missing_knowledge_chunk_target() -> None:
    payload = claim_payload(WorkerJobType.INDEX_CHUNK)
    payload["target"]["knowledgeChunkId"] = None  # type: ignore[index]

    with pytest.raises(ValidationError, match="knowledge chunk target"):
        ClaimResponse.model_validate(payload)


def test_figure_extraction_claim_accepts_a_page_image_target() -> None:
    payload = claim_payload(WorkerJobType.EXTRACT_PAGE_FIGURES)
    payload["target"] = {
        "documentId": 10,
        "documentPageId": 98,
        "pdfPageIndex": 97,
        "knowledgeChunkId": None,
        "inputAvailable": True,
    }

    claim = ClaimResponse.model_validate(payload)

    assert claim.job_type is WorkerJobType.EXTRACT_PAGE_FIGURES
    assert claim.target.document_page_id == 98


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("documentPageId", None),
        ("inputAvailable", False),
    ],
)
def test_figure_extraction_claim_rejects_invalid_page_input(
        field: str,
        value: object,
) -> None:
    payload = claim_payload(WorkerJobType.EXTRACT_PAGE_FIGURES)
    payload["target"] = {
        "documentId": 10,
        "documentPageId": 98,
        "pdfPageIndex": 97,
        "knowledgeChunkId": None,
        "inputAvailable": True,
    }
    payload["target"][field] = value  # type: ignore[index]

    with pytest.raises(ValidationError, match="document page image input"):
        ClaimResponse.model_validate(payload)


def test_claim_rejects_unknown_response_fields() -> None:
    payload = claim_payload(WorkerJobType.INDEX_CHUNK)
    payload["secretPath"] = "/unsafe"

    with pytest.raises(ValidationError, match="Extra inputs are not permitted"):
        ClaimResponse.model_validate(payload)
