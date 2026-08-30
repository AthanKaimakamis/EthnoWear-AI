import asyncio
import json
from pathlib import Path

import httpx
import pytest
from pydantic import SecretStr

from ethnowear_figure_worker.api.client import FigureWorkerApiClient
from ethnowear_figure_worker.api.models import FigureCropRequest
from ethnowear_figure_worker.config import FigureWorkerSettings
from ethnowear_worker_common.api.errors import WorkerApiContractError
from ethnowear_worker_common.jobs.context import ClaimCredentials


def settings() -> FigureWorkerSettings:
    return FigureWorkerSettings(
        api_base_url="http://worker-api.test",
        worker_id="figure-worker-1",
        api_token="x" * 32,
    )


def credentials() -> ClaimCredentials:
    return ClaimCredentials(
        job_id=41,
        claim_token=SecretStr("opaque-claim-token"),
    )


def context_payload() -> dict[str, object]:
    return {
        "jobId": 41,
        "documentId": 30,
        "documentPageId": 98,
        "documentPageMediaId": 501,
        "inputMediaAssetId": 601,
        "ocrResultId": 701,
        "ocrLayoutJson": '{"schemaVersion":2}',
        "candidates": [
            {
                "candidateId": 801,
                "candidateOrdinal": 1,
                "normalizedX": 0.1,
                "normalizedY": 0.2,
                "normalizedWidth": 0.5,
                "normalizedHeight": 0.4,
                "rawCaptionText": "Обр. 12 — Престилка",
                "detectionConfidence": 0.87,
            }
        ],
        "maximumCaptionCharacters": 2_000,
        "maximumPrintedNumberCharacters": 100,
        "maximumCropBytes": 25 * 1024 * 1024,
    }


def test_claim_sends_service_auth_and_supported_job_type() -> None:
    async def run() -> None:
        async def handler(request: httpx.Request) -> httpx.Response:
            assert request.url.path == "/api/internal/worker/jobs/claim"
            assert request.headers["Authorization"] == f"Worker {'x' * 32}"
            assert "X-Worker-Claim-Token" not in request.headers
            payload = json.loads(request.content)
            assert payload["workerId"] == "figure-worker-1"
            assert payload["supportedJobTypes"] == ["EXTRACT_PAGE_FIGURES"]
            assert payload["leaseSeconds"] == 120
            return httpx.Response(204)

        async with httpx.AsyncClient(
            transport=httpx.MockTransport(handler),
        ) as http_client:
            client = FigureWorkerApiClient(settings(), http_client)
            assert await client.claim() is None

    asyncio.run(run())


def test_context_uses_claim_credentials() -> None:
    async def run() -> None:
        async def handler(request: httpx.Request) -> httpx.Response:
            assert request.url.path == (
                "/api/internal/worker/jobs/41/figure-extraction/context"
            )
            assert request.headers["X-Worker-Id"] == "figure-worker-1"
            assert request.headers["X-Worker-Claim-Token"] == "opaque-claim-token"
            return httpx.Response(200, json=context_payload())

        async with httpx.AsyncClient(
            transport=httpx.MockTransport(handler),
        ) as http_client:
            client = FigureWorkerApiClient(settings(), http_client)
            context = await client.get_context(credentials())
            assert context.job_id == 41
            assert context.document_page_id == 98
            assert context.candidates[0].candidate_id == 801

    asyncio.run(run())


def test_context_rejects_mismatched_job() -> None:
    async def run() -> None:
        payload = context_payload()
        payload["jobId"] = 42

        async def handler(request: httpx.Request) -> httpx.Response:
            return httpx.Response(200, json=payload)

        async with httpx.AsyncClient(
            transport=httpx.MockTransport(handler),
        ) as http_client:
            client = FigureWorkerApiClient(settings(), http_client)
            with pytest.raises(WorkerApiContractError, match="different job"):
                await client.get_context(credentials())

    asyncio.run(run())


def test_download_input_uses_controlled_image_endpoint(tmp_path: Path) -> None:
    async def run() -> None:
        async def handler(request: httpx.Request) -> httpx.Response:
            assert request.url.path == "/api/internal/worker/jobs/41/input"
            assert request.headers["X-Worker-Claim-Token"] == "opaque-claim-token"
            return httpx.Response(
                200,
                headers={"Content-Type": "image/png"},
                content=b"\x89PNG\r\n\x1a\nfigure",
            )

        destination = tmp_path / "page.png"
        async with httpx.AsyncClient(
            transport=httpx.MockTransport(handler),
        ) as http_client:
            client = FigureWorkerApiClient(settings(), http_client)
            size = await client.download_input(
                credentials(),
                destination,
                maximum_bytes=1_000,
            )

        assert size == len(b"\x89PNG\r\n\x1a\nfigure")
        assert destination.read_bytes().startswith(b"\x89PNG\r\n\x1a\n")

    asyncio.run(run())


@pytest.mark.parametrize("status_code", [200, 201])
def test_upload_figure_accepts_new_and_idempotent_results(
        tmp_path: Path,
        status_code: int,
) -> None:
    async def run() -> None:
        crop_path = tmp_path / "figure.jpg"
        crop_path.write_bytes(b"\xff\xd8\xfffigure")

        async def handler(request: httpx.Request) -> httpx.Response:
            assert request.url.path == "/api/internal/worker/jobs/41/figures"
            assert request.headers["X-Worker-Claim-Token"] == "opaque-claim-token"
            assert request.headers["Content-Type"].startswith("multipart/form-data;")
            assert b'name="file"' in request.content
            assert b'name="metadata"' in request.content
            assert b'"candidateId":801' in request.content
            assert b'"figureOrdinal":1' in request.content
            return httpx.Response(
                status_code,
                json={
                    "figureId": 901,
                    "documentPageId": 98,
                    "mediaAssetId": 1_001,
                    "figureOrdinal": 1,
                    "existing": status_code == 200,
                },
            )

        async with httpx.AsyncClient(
            transport=httpx.MockTransport(handler),
        ) as http_client:
            client = FigureWorkerApiClient(settings(), http_client)
            result = await client.upload_figure(
                credentials(),
                FigureCropRequest(
                    candidate_id=801,
                    figure_ordinal=1,
                    printed_figure_number="12",
                    raw_caption_text="Обр. 12 — Престилка",
                ),
                crop_path,
                content_type="image/jpeg",
            )

        assert result.figure_id == 901
        assert result.existing is (status_code == 200)

    asyncio.run(run())


def test_upload_rejects_mismatched_ordinal(tmp_path: Path) -> None:
    async def run() -> None:
        crop_path = tmp_path / "figure.jpg"
        crop_path.write_bytes(b"\xff\xd8\xfffigure")

        async def handler(request: httpx.Request) -> httpx.Response:
            return httpx.Response(
                201,
                json={
                    "figureId": 901,
                    "documentPageId": 98,
                    "mediaAssetId": 1_001,
                    "figureOrdinal": 2,
                    "existing": False,
                },
            )

        async with httpx.AsyncClient(
            transport=httpx.MockTransport(handler),
        ) as http_client:
            client = FigureWorkerApiClient(settings(), http_client)
            with pytest.raises(WorkerApiContractError, match="ordinal"):
                await client.upload_figure(
                    credentials(),
                    FigureCropRequest(candidate_id=801, figure_ordinal=1),
                    crop_path,
                    content_type="image/jpeg",
                )

    asyncio.run(run())
