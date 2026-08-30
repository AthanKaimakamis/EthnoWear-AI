import pytest
from pydantic import ValidationError

from ethnowear_vision_worker.api.models import (
    ClaimRequest,
    DeterministicQualityStatus,
    IndexingState,
    QualityAssessmentRequest,
    QualitySignalRequest,
    QualitySignalSeverity,
    QualityStatus,
    ReviewState,
    TranscriptionApprovalState,
    VisionAssessmentContext,
    VisionAssessmentRequest,
    VisionIssueRequest,
    VisionUncertainPassageRequest,
    WorkerJobType,
)


def context_body() -> dict[str, object]:
    return {
        "jobId": 20,
        "documentId": 10,
        "documentPageId": 98,
        "ocrResultId": 51,
        "documentPageMediaId": 41,
        "inputMediaId": 31,
        "rawOcrText": "Одобрен примерен текст.",
        "ocrConfidence": 0.91,
        "ocrLanguage": "bul",
        "structuredOutputJson": None,
        "deterministicAssessmentId": 61,
        "transcriptionApprovalState": "PENDING",
        "reviewState": "REVIEW_REQUIRED",
        "indexingState": "NOT_ELIGIBLE",
        "deterministicQualityStatus": "REVIEW_REQUIRED",
        "deterministicOverallScore": 0.6,
        "deterministicSummary": "Focused review is required",
        "deterministicLimitations": None,
        "deterministicSignals": [
            {
                "type": "MIXED_SCRIPT_WORDS",
                "decimalValue": 0.1,
                "textValue": None,
                "severity": "WARNING",
                "weight": 0.8,
                "safeMessage": "Mixed scripts were detected",
            }
        ],
        "imageMimeType": "image/png",
        "imageSizeBytes": 2048,
        "imageWidth": 1200,
        "imageHeight": 1800,
        "imageDpi": 300,
        "imageColorMode": "GRAYSCALE",
    }


def assessment_request() -> VisionAssessmentRequest:
    return VisionAssessmentRequest(
        assessment=QualityAssessmentRequest(
            assessor_name="gemma3:4b",
            assessor_version="4b",
            score_version="vision-ocr-v1",
            overall_score=0.65,
            quality_status=QualityStatus.REVIEW_REQUIRED,
            summary="The OCR requires review",
            limitations="Advisory vision comparison only",
            signals=(
                QualitySignalRequest(
                    type="VISION_TEXT_MISMATCH",
                    decimal_value=0.35,
                    severity=QualitySignalSeverity.WARNING,
                    weight=1.0,
                    safe_message="Visible text differs from OCR",
                ),
            ),
        ),
        requires_review=True,
        suggested_text="Предложен текст.",
        model_name="gemma3:4b",
        model_version="4b",
        prompt_version="vision-ocr-v1",
        issues=(
            VisionIssueRequest(
                code="OCR_GARBAGE",
                original_text="719 1604",
                suggested_text=None,
                safe_message="Likely illustration-generated text",
                confidence=0.95,
            ),
        ),
        uncertain_passages=(
            VisionUncertainPassageRequest(
                excerpt="неясен откъс",
                reason="Low visual clarity",
                confidence=0.7,
            ),
        ),
    )


def test_claim_request_supports_only_vision_assessment() -> None:
    request = ClaimRequest(worker_id="vision-worker-1", lease_seconds=120)

    assert request.supported_job_types == (WorkerJobType.VISION_OCR_ASSESSMENT,)
    assert request.model_dump(by_alias=True, mode="json") == {
        "workerId": "vision-worker-1",
        "supportedJobTypes": ["VISION_OCR_ASSESSMENT"],
        "leaseSeconds": 120,
    }


def test_context_maps_exact_evidence_and_states() -> None:
    context = VisionAssessmentContext.model_validate(context_body())

    assert context.document_page_id == 98
    assert context.deterministic_assessment_id == 61
    assert context.transcription_approval_state is TranscriptionApprovalState.PENDING
    assert context.review_state is ReviewState.REVIEW_REQUIRED
    assert context.indexing_state is IndexingState.NOT_ELIGIBLE
    assert (
        context.deterministic_quality_status
        is DeterministicQualityStatus.REVIEW_REQUIRED
    )
    assert context.deterministic_signals[0].signal_type == "MIXED_SCRIPT_WORDS"


def test_context_rejects_unknown_path_data() -> None:
    body = context_body()
    body["storagePath"] = "/private/media/page.png"

    with pytest.raises(ValidationError, match="Extra inputs are not permitted"):
        VisionAssessmentContext.model_validate(body)


def test_context_rejects_invalid_confidence() -> None:
    body = context_body()
    body["ocrConfidence"] = 1.1

    with pytest.raises(ValidationError, match="ocrConfidence"):
        VisionAssessmentContext.model_validate(body)


def test_assessment_serializes_exact_spring_contract() -> None:
    body = assessment_request().model_dump(
        by_alias=True,
        mode="json",
        exclude_none=True,
    )

    assert body["requiresReview"] is True
    assert body["assessment"]["qualityStatus"] == "REVIEW_REQUIRED"
    assert body["assessment"]["signals"][0]["type"] == "VISION_TEXT_MISMATCH"
    assert body["issues"][0] == {
        "issueType": "OCR_GARBAGE",
        "explanationBg": "Likely illustration-generated text",
        "confidence": 0.95,
        "originalText": "719 1604",
        "safelyApplicable": False,
    }
    assert body["uncertainPassages"][0]["excerpt"] == "неясен откъс"


def test_assessment_rejects_out_of_range_issue_confidence() -> None:
    with pytest.raises(ValidationError, match="confidence"):
        VisionIssueRequest(code="INVALID", confidence=1.01)


def test_assessment_requires_at_least_one_quality_signal() -> None:
    with pytest.raises(ValidationError, match="signals"):
        QualityAssessmentRequest(
            assessor_name="gemma3:4b",
            assessor_version="4b",
            score_version="vision-ocr-v1",
            overall_score=0.5,
            quality_status=QualityStatus.REVIEW_REQUIRED,
            signals=(),
        )
