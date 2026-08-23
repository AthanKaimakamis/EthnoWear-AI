import asyncio
from pathlib import Path

import httpx
import pytest
from pydantic import SecretStr

from ethnowear_worker.api.client import WorkerApiClient
from ethnowear_worker.api.models import FailureRequest, ManifestPageResponse
from ethnowear_worker.api.errors import (
    WorkerApiContractError,
    WorkerApiRequestError,
    WorkerInputError,
)
from ethnowear_worker.config import WorkerSettings
from ethnowear_worker.jobs.context import ClaimCredentials
from ethnowear_worker.jobs.page_extraction import build_manifest
from ethnowear_worker.jobs.page_extraction import build_rendition_metadata
from ethnowear_worker.pdf.renderer import RenderedPage

from test_manifest import inspection


def settings() -> WorkerSettings:
    return WorkerSettings(
        base_url="http://backend:8080",
        worker_id="page-extractor-1",
        api_token="secret-worker-token-that-is-long-enough",
        poll_min_seconds=1,
        poll_max_seconds=30,
    )


def claim_body() -> dict[str, object]:
    return {
        "jobId": 11,
        "jobType": "PAGE_EXTRACTION",
        "claimToken": "opaque-claim-token",
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


def run_claim(response: httpx.Response):
    captured_request: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        captured_request.append(request)
        return response

    async def execute():
        async with httpx.AsyncClient(
            transport=httpx.MockTransport(handler)
        ) as http_client:
            client = WorkerApiClient(settings(), http_client)
            return await client.claim()

    return asyncio.run(execute()), captured_request


def test_health_uses_worker_authentication_and_validates_readiness() -> None:
    captured: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        captured.append(request)
        return httpx.Response(200, json={
            "status": "READY",
            "workerApiEnabled": True,
        })

    async def execute():
        async with httpx.AsyncClient(
            transport=httpx.MockTransport(handler)
        ) as http_client:
            return await WorkerApiClient(settings(), http_client).health()

    result = asyncio.run(execute())

    assert result.status == "READY"
    assert captured[0].method == "GET"
    assert captured[0].url == "http://backend:8080/api/internal/worker/health"
    assert captured[0].headers["Authorization"].startswith("Worker ")


def test_claim_sends_worker_authentication_and_valid_request() -> None:
    result, requests = run_claim(httpx.Response(200, json=claim_body()))

    assert result is not None
    assert result.job_id == 11
    assert len(requests) == 1
    assert requests[0].url == "http://backend:8080/api/internal/worker/jobs/claim"
    assert requests[0].headers["Authorization"] == (
        "Worker secret-worker-token-that-is-long-enough"
    )
    assert requests[0].headers["Content-Type"] == "application/json"
    assert requests[0].content == (
        b'{"workerId":"page-extractor-1",'
        b'"supportedJobTypes":["PAGE_EXTRACTION"],'
        b'"leaseSeconds":null}'
    )


def test_claim_returns_none_when_no_job_is_available() -> None:
    result, _ = run_claim(httpx.Response(204))

    assert result is None


def test_claim_maps_safe_api_error_without_exposing_token() -> None:
    with pytest.raises(WorkerApiRequestError) as captured:
        run_claim(
            httpx.Response(
                500,
                json={
                    "status": 500,
                    "error": "Internal Server Error",
                    "code": "INTERNAL_ERROR",
                    "message": "The worker request could not be completed",
                },
            )
        )

    assert captured.value.status_code == 500
    assert captured.value.code == "INTERNAL_ERROR"
    assert "secret-worker-token" not in str(captured.value)


def test_claim_rejects_malformed_success_response() -> None:
    with pytest.raises(WorkerApiContractError):
        run_claim(httpx.Response(200, json={"jobId": 11}))


def test_heartbeat_sends_attempt_credentials_and_validates_response() -> None:
    captured: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        captured.append(request)
        return httpx.Response(
            200,
            json={
                "leaseExpiresAt": "2026-08-22T10:04:00Z",
                "cancellationRequested": False,
            },
        )

    async def execute():
        async with httpx.AsyncClient(
            transport=httpx.MockTransport(handler)
        ) as http_client:
            client = WorkerApiClient(settings(), http_client)
            return await client.heartbeat(
                credentials=ClaimCredentials(
                    job_id=11,
                    claim_token=SecretStr("opaque-claim-token"),
                ),
                lease_seconds=120,
            )

    result = asyncio.run(execute())

    assert result.cancellation_requested is False
    assert captured[0].url == (
        "http://backend:8080/api/internal/worker/jobs/11/heartbeat"
    )
    assert captured[0].headers["X-Worker-Id"] == "page-extractor-1"
    assert captured[0].headers["X-Worker-Claim-Token"] == "opaque-claim-token"
    assert captured[0].content == b'{"leaseSeconds":120}'


def test_heartbeat_maps_stale_claim_error() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            409,
            json={
                "status": 409,
                "error": "Conflict",
                "code": "STALE_CLAIM",
                "message": "The worker claim is stale",
            },
        )

    async def execute():
        async with httpx.AsyncClient(
            transport=httpx.MockTransport(handler)
        ) as http_client:
            client = WorkerApiClient(settings(), http_client)
            return await client.heartbeat(
                credentials=ClaimCredentials(
                    job_id=11,
                    claim_token=SecretStr("opaque-claim-token"),
                ),
            )

    with pytest.raises(WorkerApiRequestError) as captured:
        asyncio.run(execute())

    assert captured.value.status_code == 409
    assert captured.value.code == "STALE_CLAIM"
    assert "opaque-claim-token" not in str(captured.value)


def test_download_input_streams_pdf_to_temporary_file(tmp_path: Path) -> None:
    captured: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        captured.append(request)
        return httpx.Response(
            200,
            headers={"Content-Type": "application/pdf", "Content-Length": "8"},
            content=b"%PDF-1.7",
        )

    destination = tmp_path / "input.pdf"

    async def execute() -> int:
        async with httpx.AsyncClient(
            transport=httpx.MockTransport(handler)
        ) as http_client:
            client = WorkerApiClient(settings(), http_client)
            return await client.download_input(
                ClaimCredentials(11, SecretStr("opaque-claim-token")),
                destination,
                maximum_bytes=100,
            )

    assert asyncio.run(execute()) == 8
    assert destination.read_bytes() == b"%PDF-1.7"
    assert captured[0].headers["X-Worker-Claim-Token"] == "opaque-claim-token"


def test_download_input_supports_chunked_response_without_content_length(
    tmp_path: Path,
) -> None:
    class PdfStream(httpx.AsyncByteStream):
        async def __aiter__(self):
            yield b"%PDF-"
            yield b"1.7"

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            headers={"Content-Type": "application/pdf"},
            stream=PdfStream(),
        )

    destination = tmp_path / "input.pdf"

    async def execute() -> int:
        async with httpx.AsyncClient(
            transport=httpx.MockTransport(handler)
        ) as http_client:
            client = WorkerApiClient(settings(), http_client)
            return await client.download_input(
                ClaimCredentials(11, SecretStr("opaque-claim-token")),
                destination,
                maximum_bytes=100,
            )

    assert asyncio.run(execute()) == 8
    assert destination.read_bytes() == b"%PDF-1.7"


def test_download_input_removes_partial_file_when_limit_is_exceeded(
    tmp_path: Path,
) -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            headers={"Content-Type": "application/pdf"},
            content=b"%PDF-" + b"x" * 20,
        )

    destination = tmp_path / "input.pdf"

    async def execute() -> None:
        async with httpx.AsyncClient(
            transport=httpx.MockTransport(handler)
        ) as http_client:
            client = WorkerApiClient(settings(), http_client)
            await client.download_input(
                ClaimCredentials(11, SecretStr("opaque-claim-token")),
                destination,
                maximum_bytes=10,
            )

    with pytest.raises(WorkerInputError, match="maximum size"):
        asyncio.run(execute())

    assert not destination.exists()


def test_download_input_rejects_non_pdf_content(tmp_path: Path) -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            headers={"Content-Type": "text/plain"},
            content=b"not a pdf",
        )

    async def execute() -> None:
        async with httpx.AsyncClient(
            transport=httpx.MockTransport(handler)
        ) as http_client:
            client = WorkerApiClient(settings(), http_client)
            await client.download_input(
                ClaimCredentials(11, SecretStr("opaque-claim-token")),
                tmp_path / "input.pdf",
                maximum_bytes=100,
            )

    with pytest.raises(WorkerInputError, match="PDF content type"):
        asyncio.run(execute())


def test_download_redirect_does_not_forward_worker_credentials(
    tmp_path: Path,
) -> None:
    captured: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        captured.append(request)
        if len(captured) == 1:
            return httpx.Response(
                302,
                headers={"Location": "https://media.example/input.pdf"},
            )
        return httpx.Response(
            200,
            headers={"Content-Type": "application/pdf"},
            content=b"%PDF-1.7",
        )

    async def execute() -> None:
        async with httpx.AsyncClient(
            transport=httpx.MockTransport(handler)
        ) as http_client:
            client = WorkerApiClient(settings(), http_client)
            await client.download_input(
                ClaimCredentials(11, SecretStr("opaque-claim-token")),
                tmp_path / "input.pdf",
                maximum_bytes=100,
            )

    asyncio.run(execute())

    assert len(captured) == 2
    assert "Authorization" not in captured[1].headers
    assert "X-Worker-Id" not in captured[1].headers
    assert "X-Worker-Claim-Token" not in captured[1].headers


def test_submit_manifest_returns_stable_page_ids() -> None:
    captured: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        captured.append(request)
        return httpx.Response(
            200,
            json={
                "documentId": 7,
                "pageCount": 2,
                "pages": [
                    {
                        "pageId": 21,
                        "pdfPageIndex": 0,
                        "pageSequence": 1,
                        "renditionRequired": False,
                    },
                    {
                        "pageId": 22,
                        "pdfPageIndex": 1,
                        "pageSequence": 2,
                        "renditionRequired": True,
                    },
                ],
            },
        )

    async def execute():
        async with httpx.AsyncClient(
            transport=httpx.MockTransport(handler)
        ) as http_client:
            client = WorkerApiClient(settings(), http_client)
            return await client.submit_manifest(
                ClaimCredentials(11, SecretStr("opaque-claim-token")),
                build_manifest(inspection()),
            )

    result = asyncio.run(execute())

    assert result.pages[1].page_id == 22
    assert captured[0].url == (
        "http://backend:8080/api/internal/worker/jobs/11/"
        "page-extraction/manifest"
    )
    assert captured[0].headers["X-Worker-Claim-Token"] == "opaque-claim-token"
    assert captured[0].content == (
        b'{"totalPageCount":2,"pages":['
        b'{"pdfPageIndex":0,"pageSequence":1},'
        b'{"pdfPageIndex":1,"pageSequence":2}]}'
    )


def test_submit_manifest_maps_conflict() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            409,
            json={
                "status": 409,
                "error": "Conflict",
                "code": "MANIFEST_CONFLICT",
                "message": "Manifest conflicts with existing pages",
            },
        )

    async def execute():
        async with httpx.AsyncClient(
            transport=httpx.MockTransport(handler)
        ) as http_client:
            client = WorkerApiClient(settings(), http_client)
            return await client.submit_manifest(
                ClaimCredentials(11, SecretStr("opaque-claim-token")),
                build_manifest(inspection()),
            )

    with pytest.raises(WorkerApiRequestError) as captured:
        asyncio.run(execute())

    assert captured.value.code == "MANIFEST_CONFLICT"


@pytest.mark.parametrize(("status_code", "existing"), [(201, False), (200, True)])
def test_upload_rendition_sends_multipart_and_accepts_idempotent_result(
    tmp_path: Path,
    status_code: int,
    existing: bool,
) -> None:
    captured: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        captured.append(request)
        return httpx.Response(
            status_code,
            json={
                "pageId": 21,
                "pageMediaId": 31,
                "mediaAssetId": 41,
                "renditionType": "PDF_PAGE_RENDER",
                "existing": existing,
            },
        )

    page = ManifestPageResponse(
        page_id=21,
        pdf_page_index=0,
        page_sequence=1,
        rendition_required=True,
    )
    image_path = tmp_path / "page-21.jpg"
    image_path.write_bytes(b"\xff\xd8jpeg")
    rendered = RenderedPage(
        image_path, "image/jpeg", 6, 300, 600, "RGB"
    )
    metadata = build_rendition_metadata(page, rendered, 300)

    async def execute():
        async with httpx.AsyncClient(
            transport=httpx.MockTransport(handler)
        ) as http_client:
            client = WorkerApiClient(settings(), http_client)
            return await client.upload_rendition(
                ClaimCredentials(11, SecretStr("opaque-claim-token")),
                page.page_id,
                metadata,
                rendered,
            )

    result = asyncio.run(execute())

    assert result.existing is existing
    assert captured[0].url == (
        "http://backend:8080/api/internal/worker/jobs/11/"
        "pages/21/renditions"
    )
    assert captured[0].headers["Content-Type"].startswith("multipart/form-data;")
    assert b'name="file"' in captured[0].content
    assert b'filename="page-21.jpg"' in captured[0].content
    assert b"Content-Type: image/jpeg" in captured[0].content
    assert b'name="metadata"' in captured[0].content
    assert b'application/json' in captured[0].content
    assert b'"pdfPageIndex":0' in captured[0].content
    assert b"\xff\xd8jpeg" in captured[0].content


def test_upload_rendition_rejects_mismatched_response_page(
    tmp_path: Path,
) -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            201,
            json={
                "pageId": 99,
                "pageMediaId": 31,
                "mediaAssetId": 41,
                "renditionType": "PDF_PAGE_RENDER",
                "existing": False,
            },
        )

    page = ManifestPageResponse(
        page_id=21,
        pdf_page_index=0,
        page_sequence=1,
        rendition_required=True,
    )
    image_path = tmp_path / "page-21.jpg"
    image_path.write_bytes(b"\xff\xd8jpeg")
    rendered = RenderedPage(
        image_path, "image/jpeg", 6, 300, 600, "RGB"
    )

    async def execute():
        async with httpx.AsyncClient(
            transport=httpx.MockTransport(handler)
        ) as http_client:
            client = WorkerApiClient(settings(), http_client)
            return await client.upload_rendition(
                ClaimCredentials(11, SecretStr("opaque-claim-token")),
                page.page_id,
                build_rendition_metadata(page, rendered, 300),
                rendered,
            )

    with pytest.raises(WorkerApiContractError, match="different page"):
        asyncio.run(execute())


@pytest.mark.parametrize("operation", ["complete", "fail"])
def test_terminal_operation_sends_claim_headers(operation: str) -> None:
    captured: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        captured.append(request)
        if operation == "complete":
            return httpx.Response(200, json={
                "jobId": 11,
                "documentId": 7,
                "pageCount": 2,
                "queuedOcrJobs": 2,
                "existing": False,
            })
        return httpx.Response(200, json={
            "jobId": 11,
            "status": "RETRY_WAIT",
            "availableAt": "2026-08-22T10:05:00Z",
            "existing": False,
        })

    async def execute():
        async with httpx.AsyncClient(
            transport=httpx.MockTransport(handler)
        ) as http_client:
            client = WorkerApiClient(settings(), http_client)
            credentials = ClaimCredentials(11, SecretStr("opaque-claim-token"))
            if operation == "complete":
                return await client.complete(credentials)
            return await client.fail(credentials, FailureRequest(
                error_code="PDF_RENDER_FAILED",
                safe_error_message="The PDF page could not be rendered",
                retryable=True,
            ))

    result = asyncio.run(execute())

    assert result.job_id == 11
    assert captured[0].url == (
        f"http://backend:8080/api/internal/worker/jobs/11/{operation}"
    )
    assert captured[0].headers["Authorization"].startswith("Worker ")
    assert captured[0].headers["X-Worker-Id"] == "page-extractor-1"
    assert captured[0].headers["X-Worker-Claim-Token"] == "opaque-claim-token"
    if operation == "fail":
        assert captured[0].content == (
            b'{"errorCode":"PDF_RENDER_FAILED",'
            b'"safeErrorMessage":"The PDF page could not be rendered",'
            b'"retryable":true}'
        )


@pytest.mark.parametrize("operation", ["complete", "fail"])
def test_terminal_operation_rejects_mismatched_job(operation: str) -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        if operation == "complete":
            return httpx.Response(200, json={
                "jobId": 99, "documentId": 7, "pageCount": 1,
                "queuedOcrJobs": 1, "existing": False,
            })
        return httpx.Response(200, json={
            "jobId": 99, "status": "FAILED",
            "availableAt": None, "existing": False,
        })

    async def execute():
        async with httpx.AsyncClient(
            transport=httpx.MockTransport(handler)
        ) as http_client:
            client = WorkerApiClient(settings(), http_client)
            credentials = ClaimCredentials(11, SecretStr("opaque-claim-token"))
            if operation == "complete":
                return await client.complete(credentials)
            return await client.fail(credentials, FailureRequest(
                error_code="PDF_RENDER_FAILED",
                safe_error_message="Safe failure",
                retryable=False,
            ))

    with pytest.raises(WorkerApiContractError, match="different job"):
        asyncio.run(execute())


@pytest.mark.parametrize(
    ("job_id", "status", "error"),
    [
        (11, "CANCELLED", None),
        (99, "CANCELLED", "different job"),
        (11, "RUNNING", "invalid status"),
    ],
)
def test_acknowledge_cancellation_validates_contract(
    job_id: int,
    status: str,
    error: str | None,
) -> None:
    captured: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        captured.append(request)
        return httpx.Response(200, json={
            "jobId": job_id,
            "status": status,
            "finishedAt": "2026-08-22T10:06:00Z",
            "existing": False,
        })

    async def execute():
        async with httpx.AsyncClient(
            transport=httpx.MockTransport(handler)
        ) as http_client:
            return await WorkerApiClient(
                settings(), http_client
            ).acknowledge_cancellation(
                ClaimCredentials(11, SecretStr("opaque-claim-token"))
            )

    if error:
        with pytest.raises(WorkerApiContractError, match=error):
            asyncio.run(execute())
    else:
        result = asyncio.run(execute())
        assert result.job_id == 11

    assert captured[0].url == (
        "http://backend:8080/api/internal/worker/jobs/11/cancelled"
    )
    assert captured[0].headers["X-Worker-Id"] == "page-extractor-1"
    assert captured[0].headers["X-Worker-Claim-Token"] == "opaque-claim-token"
    assert captured[0].content == b""
