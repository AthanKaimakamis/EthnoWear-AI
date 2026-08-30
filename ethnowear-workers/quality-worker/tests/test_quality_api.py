import asyncio
import json

import httpx
from pydantic import SecretStr

from ethnowear_quality_worker.api.client import QualityWorkerApiClient
from ethnowear_quality_worker.api.models import (
    QualityAssessmentRequest,
    QualitySignalRequest,
    QualitySignalSeverity,
    QualityStatus,
)
from ethnowear_quality_worker.config import QualityWorkerSettings
from ethnowear_worker_common.jobs.context import ClaimCredentials


def settings() -> QualityWorkerSettings:
    return QualityWorkerSettings(
        api_base_url="http://backend:8080",
        worker_id="quality-worker-1",
        api_token="secret-worker-token-that-is-long-enough",
    )


def credentials() -> ClaimCredentials:
    return ClaimCredentials(13, SecretStr("opaque-claim-token"))


def assessment() -> QualityAssessmentRequest:
    return QualityAssessmentRequest(
        assessor_name="ethnowear-deterministic-quality",
        assessor_version="1.0.0",
        score_version="1",
        overall_score=0.92,
        quality_status=QualityStatus.PASS,
        summary="Deterministic OCR checks passed",
        limitations="Human review is still required",
        signals=(QualitySignalRequest(
            type="OCR_CONFIDENCE",
            decimal_value=0.92,
            severity=QualitySignalSeverity.INFO,
            weight=0.2,
            safe_message="Mean OCR confidence was evaluated",
        ),),
    )


def test_get_quality_context_uses_claim_headers_and_parses_contract() -> None:
    captured: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        captured.append(request)
        return httpx.Response(200, json={
            "jobId": 13,
            "documentId": 7,
            "pageId": 21,
            "ocrResultId": 31,
            "pageMediaId": 41,
            "inputMediaId": 51,
            "rawOcrText": "Българска шевица",
            "ocrConfidence": 0.92,
            "ocrLanguage": "bul",
            "structuredOutputJson": '{"schemaVersion":1,"words":[]}',
            "imageMimeType": "image/png",
            "imageSizeBytes": 50_000,
            "imageWidth": 2_000,
            "imageHeight": 3_000,
            "imageDpi": 300,
            "imageColorMode": "GRAYSCALE",
        })

    async def execute():
        async with httpx.AsyncClient(
            transport=httpx.MockTransport(handler)
        ) as http_client:
            return await QualityWorkerApiClient(
                settings(),
                http_client,
            ).get_quality_assessment_context(credentials())

    result = asyncio.run(execute())

    assert result.ocr_result_id == 31
    assert captured[0].method == "GET"
    assert captured[0].url.path.endswith(
        "/jobs/13/quality-assessment/context"
    )
    assert captured[0].headers["X-Worker-Claim-Token"] == "opaque-claim-token"


def test_submit_quality_assessment_serializes_and_parses_idempotency() -> None:
    captured: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        captured.append(request)
        return httpx.Response(200, json={
            "assessmentId": 61,
            "jobId": 13,
            "documentId": 7,
            "pageId": 21,
            "ocrResultId": 31,
            "inputMediaId": 51,
            "existing": True,
        })

    async def execute():
        async with httpx.AsyncClient(
            transport=httpx.MockTransport(handler)
        ) as http_client:
            return await QualityWorkerApiClient(
                settings(),
                http_client,
            ).submit_quality_assessment(credentials(), assessment())

    result = asyncio.run(execute())
    payload = json.loads(captured[0].content)

    assert result.existing is True
    assert captured[0].url.path.endswith("/jobs/13/quality-assessment")
    assert payload["qualityStatus"] == "PASS"
    assert payload["signals"][0]["type"] == "OCR_CONFIDENCE"
    assert "signalType" not in payload["signals"][0]


def test_claim_advertises_only_quality_assessment() -> None:
    captured: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        captured.append(request)
        return httpx.Response(204)

    async def execute():
        async with httpx.AsyncClient(
            transport=httpx.MockTransport(handler)
        ) as http_client:
            return await QualityWorkerApiClient(settings(), http_client).claim()

    assert asyncio.run(execute()) is None
    payload = json.loads(captured[0].content)
    assert payload["supportedJobTypes"] == ["OCR_QUALITY_ASSESSMENT"]
