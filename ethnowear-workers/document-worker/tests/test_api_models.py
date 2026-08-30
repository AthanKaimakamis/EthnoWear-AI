import pytest
from pydantic import ValidationError

from ethnowear_document_worker.api.models import (
    ClaimRequest,
    ClaimResponse,
    CancellationResponse,
    CompletionResponse,
    FailureRequest,
    FailureResponse,
    FigureCandidateRequest,
    HeartbeatRequest,
    HeartbeatResponse,
    JobStatus,
    JobTarget,
    OcrResultRequest,
    OcrResultResponse,
    ReadinessResponse,
    WorkerJobType,
)


def test_figure_candidate_serializes_for_spring() -> None:
    candidate = FigureCandidateRequest(
        candidate_ordinal=1,
        normalized_x=0.1,
        normalized_y=0.2,
        normalized_width=0.5,
        normalized_height=0.4,
        raw_caption_text="Обр. 12 — Престилка",
        detection_confidence=0.875,
    )

    assert candidate.model_dump(by_alias=True, mode="json") == {
        "candidateOrdinal": 1,
        "normalizedX": 0.1,
        "normalizedY": 0.2,
        "normalizedWidth": 0.5,
        "normalizedHeight": 0.4,
        "rawCaptionText": "Обр. 12 — Престилка",
        "detectionConfidence": 0.875,
    }


@pytest.mark.parametrize(
    "changes",
    [
        {"normalized_x": -0.1},
        {"normalized_width": 0.0},
        {"normalized_x": 0.8, "normalized_width": 0.3},
        {"normalized_y": 0.8, "normalized_height": 0.3},
    ],
)
def test_figure_candidate_rejects_invalid_bounds(
        changes: dict[str, float],
) -> None:
    values = {
        "candidate_ordinal": 1,
        "normalized_x": 0.1,
        "normalized_y": 0.2,
        "normalized_width": 0.5,
        "normalized_height": 0.4,
        "detection_confidence": 0.8,
    }
    values.update(changes)

    with pytest.raises(ValidationError):
        FigureCandidateRequest(**values)


def test_ocr_result_serializes_figure_candidates() -> None:
    request = OcrResultRequest(
        raw_text="Текст",
        ocr_engine="tesseract",
        figure_candidates=(
            FigureCandidateRequest(
                candidate_ordinal=1,
                normalized_x=0.1,
                normalized_y=0.2,
                normalized_width=0.5,
                normalized_height=0.4,
                detection_confidence=0.8,
            ),
        ),
    )

    payload = request.model_dump(by_alias=True, mode="json")

    assert len(payload["figureCandidates"]) == 1
    assert payload["figureCandidates"][0]["candidateOrdinal"] == 1


def test_readiness_response_requires_ready_enabled_api() -> None:
    response = ReadinessResponse.model_validate({
        "status": "READY",
        "workerApiEnabled": True,
    })

    assert response.worker_api_enabled is True

    with pytest.raises(ValidationError):
        ReadinessResponse.model_validate({
            "status": "READY",
            "workerApiEnabled": False,
        })


def test_claim_request_serializes_using_spring_field_names() -> None:
    request = ClaimRequest(worker_id="page-extractor-1", lease_seconds=120)

    assert request.model_dump(by_alias=True, mode="json") == {
        "workerId": "page-extractor-1",
        "supportedJobTypes": [
            "PAGE_EXTRACTION",
            "OCR",
        ],
        "leaseSeconds": 120,
    }


def test_claim_response_validates_and_maps_nested_limits() -> None:
    response = ClaimResponse.model_validate(
        {
            "jobId": 11,
            "jobType": "PAGE_EXTRACTION",
            "claimToken": "opaque-token",
            "attempt": 1,
            "claimedAt": "2026-08-22T10:00:00Z",
            "leaseExpiresAt": "2026-08-22T10:02:00Z",
            "target": {
                "documentId": 7,
                "documentPageId": None,
                "pdfPageIndex": 97,
                "knowledgeChunkId": None,
                "inputAvailable": True,
            },
            "limits": {
                "maximumInputBytes": 262_144_000,
                "maximumPageCount": 2_000,
                "renderDpi": 300,
                "maximumPixelWidth": 20_000,
                "maximumPixelHeight": 20_000,
                "maximumPagePixels": 100_000_000,
                "maximumRenditionBytes": 26_214_400,
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
                "maximumFigureCandidates": 100,
                "maximumFigureCaptionCharacters": 2_000,
                "maximumPrintedFigureNumberCharacters": 100,
                "maximumFigureCropBytes": 25 * 1024 * 1024,
                "jobTimeoutSeconds": 1_800,
                "heartbeatIntervalSeconds": 30,
                "maximumLeaseSeconds": 300,
            },
        }
    )

    assert response.job_id == 11
    assert response.target.document_id == 7
    assert response.target.pdf_page_index == 97
    assert response.limits.render_dpi == 300
    assert response.claim_token.get_secret_value() == "opaque-token"
    assert "opaque-token" not in repr(response)


def test_claim_response_rejects_negative_pdf_page_index() -> None:
    payload = {
        "documentId": 7,
        "documentPageId": 21,
        "pdfPageIndex": -1,
        "knowledgeChunkId": None,
        "inputAvailable": True,
    }

    with pytest.raises(ValidationError):
        JobTarget.model_validate(payload)


def test_claim_response_rejects_unknown_contract_fields() -> None:
    with pytest.raises(ValidationError):
        ClaimResponse.model_validate({"unexpected": "value"})


def test_claim_response_rejects_invalid_page_extraction_lease() -> None:
    payload = {
        "jobId": 11, "jobType": "PAGE_EXTRACTION", "claimToken": "token",
        "attempt": 1, "claimedAt": "2026-08-22T10:00:00Z",
        "leaseExpiresAt": "2026-08-22T10:01:00Z",
        "target": {"documentId": 7, "documentPageId": None,
                   "knowledgeChunkId": None, "inputAvailable": True},
        "limits": {"maximumInputBytes": 1, "maximumPageCount": 1,
                   "renderDpi": 72, "maximumPixelWidth": 100,
                   "maximumPixelHeight": 100, "maximumPagePixels": 10_000,
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
                   "maximumVisionAssessmentPayloadBytes": 1,
                   "maximumVisionSuggestionCharacters": 1,
                   "maximumVisionIssuesJsonCharacters": 1,
                   "maximumVisionIssues": 1,
                   "maximumVisionUncertainPassages": 1,
                   "maximumVisionIssueCodeCharacters": 1,
                   "maximumVisionExcerptCharacters": 1,
                   "maximumVisionReasonCharacters": 1,
                   "maximumVisionModelNameCharacters": 1,
                   "maximumVisionModelVersionCharacters": 1,
                   "maximumVisionPromptVersionCharacters": 1,
                   "maximumIndexingContentCharacters": 1,
                   "maximumEmbeddingDimensions": 1,
                   "maximumEmbeddingModelCharacters": 1,
                   "maximumVectorCollectionCharacters": 1,
                   "maximumVectorPointIdCharacters": 1,
                   "maximumFigureCandidates": 1,
                   "maximumFigureCaptionCharacters": 1,
                   "maximumPrintedFigureNumberCharacters": 1,
                   "maximumFigureCropBytes": 1,
                   "jobTimeoutSeconds": 60, "heartbeatIntervalSeconds": 60,
                   "maximumLeaseSeconds": 60},
    }

    with pytest.raises(ValidationError):
        ClaimResponse.model_validate(payload)


def test_claim_response_accepts_valid_ocr_target() -> None:
    payload = {
        "jobId": 12,
        "jobType": "OCR",
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
            "maximumFigureCandidates": 100,
            "maximumFigureCaptionCharacters": 2_000,
            "maximumPrintedFigureNumberCharacters": 100,
            "maximumFigureCropBytes": 25 * 1024 * 1024,
            "jobTimeoutSeconds": 1_800,
            "heartbeatIntervalSeconds": 30,
            "maximumLeaseSeconds": 300,
        },
    }

    response = ClaimResponse.model_validate(payload)

    assert response.job_type is WorkerJobType.OCR
    assert response.target.document_page_id == 21
    assert response.limits.maximum_ocr_text_characters == 1_000_000

    payload["target"]["documentPageId"] = None
    with pytest.raises(ValidationError):
        ClaimResponse.model_validate(payload)


def test_heartbeat_models_match_spring_contract() -> None:
    request = HeartbeatRequest(lease_seconds=120)
    response = HeartbeatResponse.model_validate(
        {
            "leaseExpiresAt": "2026-08-22T10:04:00Z",
            "cancellationRequested": False,
        }
    )

    assert request.model_dump(by_alias=True, mode="json") == {
        "leaseSeconds": 120,
    }
    assert response.lease_expires_at.isoformat() == "2026-08-22T10:04:00+00:00"
    assert response.cancellation_requested is False


def test_terminal_models_match_spring_contract() -> None:
    failure = FailureRequest(
        error_code="PDF_RENDER_FAILED",
        safe_error_message="The PDF page could not be rendered",
        retryable=True,
    )
    failed = FailureResponse.model_validate({
        "jobId": 11,
        "status": "RETRY_WAIT",
        "availableAt": "2026-08-22T10:05:00Z",
        "existing": False,
    })
    completed = CompletionResponse.model_validate({
        "jobId": 11,
        "jobType": "PAGE_EXTRACTION",
        "documentId": 7,
        "documentPageId": None,
        "pageCount": 2,
        "queuedOcrJobs": 2,
        "queuedQualityAssessmentJobs": 0,
        "queuedVisionAssessmentJobs": 0,
        "existing": False,
    })

    assert failure.model_dump(by_alias=True, mode="json") == {
        "errorCode": "PDF_RENDER_FAILED",
        "safeErrorMessage": "The PDF page could not be rendered",
        "retryable": True,
    }
    assert failed.status is JobStatus.RETRY_WAIT
    assert completed.queued_ocr_jobs == 2


def test_ocr_result_models_match_spring_contract() -> None:
    request = OcrResultRequest(
        raw_text="Български текст",
        ocr_engine="tesseract",
        ocr_engine_version="5.5.0",
        ocr_language="bul",
        ocr_confidence=0.875,
        parameters_json='{"oem":1,"psm":3}',
        structured_output_json='{"words":[]}',
    )
    response = OcrResultResponse.model_validate({
        "ocrResultId": 31,
        "jobId": 12,
        "documentId": 7,
        "pageId": 21,
        "inputMediaId": 41,
        "existing": False,
    })

    assert request.model_dump(by_alias=True, mode="json") == {
        "rawText": "Български текст",
        "ocrEngine": "tesseract",
        "ocrEngineVersion": "5.5.0",
        "ocrLanguage": "bul",
        "ocrConfidence": 0.875,
            "parametersJson": '{"oem":1,"psm":3}',
            "structuredOutputJson": '{"words":[]}',
            "figureCandidates": [],
        }
    assert response.page_id == 21

    with pytest.raises(ValidationError):
        OcrResultRequest(
            raw_text="text",
            ocr_engine="tesseract",
            ocr_confidence=1.01,
        )


def test_ocr_completion_response_matches_spring_contract() -> None:
    completed = CompletionResponse.model_validate({
        "jobId": 12,
        "jobType": "OCR",
        "documentId": 7,
        "documentPageId": 21,
        "pageCount": 0,
        "queuedOcrJobs": 0,
        "queuedQualityAssessmentJobs": 1,
        "queuedVisionAssessmentJobs": 0,
        "existing": False,
    })

    assert completed.job_type is WorkerJobType.OCR
    assert completed.document_page_id == 21
    assert completed.queued_quality_assessment_jobs == 1


def test_failure_request_rejects_unsafe_error_code() -> None:
    with pytest.raises(ValidationError):
        FailureRequest(
            error_code="bad-code",
            safe_error_message="Safe message",
            retryable=False,
        )


def test_cancellation_response_matches_spring_contract() -> None:
    response = CancellationResponse.model_validate({
        "jobId": 11,
        "status": "CANCELLED",
        "finishedAt": "2026-08-22T10:06:00Z",
        "existing": False,
    })

    assert response.job_id == 11
    assert response.status is JobStatus.CANCELLED
    assert response.finished_at.isoformat() == "2026-08-22T10:06:00+00:00"
