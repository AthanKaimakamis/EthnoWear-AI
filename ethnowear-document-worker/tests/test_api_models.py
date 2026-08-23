import pytest
from pydantic import ValidationError

from ethnowear_worker.api.models import (
    ClaimRequest,
    ClaimResponse,
    CancellationResponse,
    CompletionResponse,
    FailureRequest,
    FailureResponse,
    HeartbeatRequest,
    HeartbeatResponse,
    JobStatus,
    ReadinessResponse,
)


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
        "supportedJobTypes": ["PAGE_EXTRACTION"],
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
                "jobTimeoutSeconds": 1_800,
                "heartbeatIntervalSeconds": 30,
                "maximumLeaseSeconds": 300,
            },
        }
    )

    assert response.job_id == 11
    assert response.target.document_id == 7
    assert response.limits.render_dpi == 300
    assert response.claim_token.get_secret_value() == "opaque-token"
    assert "opaque-token" not in repr(response)


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
                   "jobTimeoutSeconds": 60, "heartbeatIntervalSeconds": 60,
                   "maximumLeaseSeconds": 60},
    }

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
        "documentId": 7,
        "pageCount": 2,
        "queuedOcrJobs": 2,
        "existing": False,
    })

    assert failure.model_dump(by_alias=True, mode="json") == {
        "errorCode": "PDF_RENDER_FAILED",
        "safeErrorMessage": "The PDF page could not be rendered",
        "retryable": True,
    }
    assert failed.status is JobStatus.RETRY_WAIT
    assert completed.queued_ocr_jobs == 2


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
