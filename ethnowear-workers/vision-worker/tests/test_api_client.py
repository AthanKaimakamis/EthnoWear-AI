import json
from pathlib import Path

import httpx
import pytest
from pydantic import SecretStr

from ethnowear_worker_common.api.errors import (
    WorkerApiContractError,
    WorkerInputError,
)
from ethnowear_worker_common.jobs.context import ClaimCredentials
from ethnowear_vision_worker.api.client import VisionWorkerApiClient
from ethnowear_vision_worker.api.models import (
    QualityAssessmentRequest,
    QualitySignalRequest,
    QualitySignalSeverity,
    QualityStatus,
    VisionAssessmentRequest,
)
from ethnowear_vision_worker.config import VisionWorkerSettings


def settings() -> VisionWorkerSettings:
    return VisionWorkerSettings(
        api_base_url="http://api:8080",
        worker_id="vision-worker-1",
        api_token="x" * 32,
        ollama_base_url="http://ollama:11434",
        vision_model="gemma3:4b",
        prompt_version="vision-ocr-v1",
    )


def credentials() -> ClaimCredentials:
    return ClaimCredentials(job_id=20, claim_token=SecretStr("opaque-token"))


def assert_claim_headers(request: httpx.Request) -> None:
    assert request.headers["Authorization"] == "Worker " + "x" * 32
    assert request.headers["X-Worker-Id"] == "vision-worker-1"
    assert request.headers["X-Worker-Claim-Token"] == "opaque-token"


def context_body(job_id: int = 20) -> dict[str, object]:
    return {
        "jobId": job_id,
        "documentId": 10,
        "documentPageId": 98,
        "ocrResultId": 51,
        "documentPageMediaId": 41,
        "inputMediaId": 31,
        "rawOcrText": "Разпознат текст.",
        "ocrConfidence": 0.9,
        "ocrLanguage": "bul",
        "structuredOutputJson": None,
        "deterministicAssessmentId": 61,
        "transcriptionApprovalState": "PENDING",
        "reviewState": "REVIEW_REQUIRED",
        "indexingState": "NOT_ELIGIBLE",
        "deterministicQualityStatus": "REVIEW_REQUIRED",
        "deterministicOverallScore": 0.6,
        "deterministicSummary": "Review required",
        "deterministicLimitations": None,
        "deterministicSignals": [],
        "imageMimeType": "image/png",
        "imageSizeBytes": 12,
        "imageWidth": 100,
        "imageHeight": 200,
        "imageDpi": 300,
        "imageColorMode": "GRAYSCALE",
    }


def assessment_request() -> VisionAssessmentRequest:
    return VisionAssessmentRequest(
        assessment=QualityAssessmentRequest(
            assessor_name="gemma3:4b",
            assessor_version="4b",
            score_version="vision-ocr-v1",
            overall_score=0.7,
            quality_status=QualityStatus.REVIEW_REQUIRED,
            signals=(
                QualitySignalRequest(
                    type="VISION_REVIEW_REQUIRED",
                    severity=QualitySignalSeverity.WARNING,
                ),
            ),
        ),
        requires_review=True,
        suggested_text="Предложен текст.",
        model_name="gemma3:4b",
        model_version="4b",
        prompt_version="vision-ocr-v1",
        issues=(),
        uncertain_passages=(),
    )


@pytest.mark.asyncio
async def test_claim_requests_only_vision_jobs() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/api/internal/worker/jobs/claim"
        assert request.headers["Authorization"] == "Worker " + "x" * 32
        assert "X-Worker-Claim-Token" not in request.headers
        assert json.loads(request.content) == {
            "workerId": "vision-worker-1",
            "supportedJobTypes": ["VISION_OCR_ASSESSMENT"],
            "leaseSeconds": 120,
        }
        return httpx.Response(204)

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        assert await VisionWorkerApiClient(settings(), http).claim() is None


@pytest.mark.asyncio
async def test_context_uses_claim_credentials() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == (
            "/api/internal/worker/jobs/20/vision-assessment/context"
        )
        assert_claim_headers(request)
        return httpx.Response(200, json=context_body())

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        result = await VisionWorkerApiClient(settings(), http).get_assessment_context(
            credentials()
        )

    assert result.ocr_result_id == 51


@pytest.mark.asyncio
async def test_context_rejects_mismatched_job() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json=context_body(job_id=21))

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        with pytest.raises(WorkerApiContractError, match="different job"):
            await VisionWorkerApiClient(settings(), http).get_assessment_context(
                credentials()
            )


@pytest.mark.asyncio
async def test_download_input_streams_supported_image(tmp_path: Path) -> None:
    image = b"\x89PNG\r\n\x1a\nbody"

    async def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/api/internal/worker/jobs/20/input"
        assert_claim_headers(request)
        return httpx.Response(
            200,
            headers={"Content-Type": "image/png"},
            content=image,
        )

    destination = tmp_path / "input.png"
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        size = await VisionWorkerApiClient(settings(), http).download_input(
            credentials(), destination, 1024
        )

    assert size == len(image)
    assert destination.read_bytes() == image
    assert destination.stat().st_mode & 0o777 == 0o600


@pytest.mark.asyncio
async def test_download_input_removes_oversized_partial_file(tmp_path: Path) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            headers={"Content-Type": "image/png"},
            content=b"\x89PNG\r\n\x1a\nbody",
        )

    destination = tmp_path / "input.png"
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        with pytest.raises(WorkerInputError, match="maximum size"):
            await VisionWorkerApiClient(settings(), http).download_input(
                credentials(), destination, 8
            )

    assert not destination.exists()


@pytest.mark.asyncio
async def test_redirect_does_not_forward_worker_credentials(tmp_path: Path) -> None:
    image = b"\xff\xd8\xffbody"

    async def handler(request: httpx.Request) -> httpx.Response:
        if request.url.host == "api":
            assert_claim_headers(request)
            return httpx.Response(
                302,
                headers={"Location": "http://media:8081/page.jpg"},
            )

        assert request.url.host == "media"
        assert "Authorization" not in request.headers
        assert "X-Worker-Claim-Token" not in request.headers
        return httpx.Response(
            200,
            headers={"Content-Type": "image/jpeg"},
            content=image,
        )

    destination = tmp_path / "input.jpg"
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        await VisionWorkerApiClient(settings(), http).download_input(
            credentials(), destination, 1024
        )

    assert destination.read_bytes() == image


@pytest.mark.asyncio
async def test_submit_assessment_uses_claim_credentials() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/api/internal/worker/jobs/20/vision-assessment"
        assert_claim_headers(request)
        body = json.loads(request.content)
        assert body["requiresReview"] is True
        assert body["modelName"] == "gemma3:4b"
        assert body["assessment"]["qualityStatus"] == "REVIEW_REQUIRED"
        return httpx.Response(
            201,
            json={
                "assessmentId": 71,
                "suggestionId": 81,
                "jobId": 20,
                "documentId": 10,
                "pageId": 98,
                "ocrResultId": 51,
                "inputMediaId": 31,
                "existing": False,
            },
        )

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        result = await VisionWorkerApiClient(settings(), http).submit_assessment(
            credentials(), assessment_request()
        )

    assert result.assessment_id == 71
    assert result.existing is False
