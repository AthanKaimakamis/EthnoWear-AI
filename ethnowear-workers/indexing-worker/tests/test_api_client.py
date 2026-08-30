import json

import httpx
import pytest
from pydantic import SecretStr

from ethnowear_indexer.api.client import IndexerApiClient
from ethnowear_indexer.api.models import FailureRequest, IndexResultRequest
from ethnowear_indexer.config import IndexerSettings
from ethnowear_worker_common.jobs.context import ClaimCredentials


CONTENT_HASH = "a" * 64


def settings() -> IndexerSettings:
    return IndexerSettings(
        api_base_url="http://api:8080",
        worker_id="indexer-1",
        api_token="x" * 32,
        qdrant_url="http://qdrant:6333",
        qdrant_collection="chunks",
    )


def credentials() -> ClaimCredentials:
    return ClaimCredentials(job_id=20, claim_token=SecretStr("opaque-token"))


def assert_claim_headers(request: httpx.Request) -> None:
    assert request.headers["Authorization"] == "Worker " + "x" * 32
    assert request.headers["X-Worker-Id"] == "indexer-1"
    assert request.headers["X-Worker-Claim-Token"] == "opaque-token"


@pytest.mark.asyncio
async def test_claim_requests_only_index_chunk() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/api/internal/worker/jobs/claim"
        assert request.headers["Authorization"] == "Worker " + "x" * 32
        assert "X-Worker-Claim-Token" not in request.headers
        assert json.loads(request.content) == {
            "workerId": "indexer-1",
            "supportedJobTypes": ["INDEX_CHUNK"],
            "leaseSeconds": 120,
        }
        return httpx.Response(204)

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        assert await IndexerApiClient(settings(), http).claim() is None


@pytest.mark.asyncio
async def test_get_indexing_context_uses_claim_credentials() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/api/internal/worker/jobs/20/indexing-context"
        assert_claim_headers(request)
        return httpx.Response(
            200,
            json={
                "jobId": 20,
                "knowledgeChunkId": 99,
                "content": "Одобрен текст.",
                "contentHash": CONTENT_HASH,
                "language": "bg",
                "chunkType": "BOOK_EXCERPT",
                "documentId": 10,
                "sourceReferenceId": None,
                "archiveItemId": None,
                "ontologyIri": None,
                "provenanceTrustState": "VERIFIED",
                "transcriptionApprovalState": "APPROVED",
            },
        )

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        result = await IndexerApiClient(settings(), http).get_indexing_context(credentials())

    assert result.knowledge_chunk_id == 99


@pytest.mark.asyncio
async def test_submit_index_result_uses_claim_credentials() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        assert_claim_headers(request)
        assert json.loads(request.content)["embeddingDimensions"] == 1024
        return httpx.Response(
            201,
            json={"jobId": 20, "knowledgeChunkId": 99, "existing": False},
        )

    result = IndexResultRequest(
        content_hash=CONTENT_HASH,
        embedding_model="bge-m3",
        embedding_dimensions=1024,
        vector_collection="chunks",
        vector_point_id="99",
    )
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        response = await IndexerApiClient(settings(), http).submit_index_result(
            credentials(), result
        )

    assert response.existing is False


@pytest.mark.asyncio
async def test_get_embedding_uses_claim_credentials_and_parses_vector() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/api/internal/worker/jobs/20/embedding"
        assert_claim_headers(request)
        return httpx.Response(200, json={
            "jobId": 20,
            "knowledgeChunkId": 99,
            "contentHash": CONTENT_HASH,
            "embeddingModel": "bge-m3",
            "embeddingDimensions": 3,
            "values": [0.1, 0.2, 0.3],
        })

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        result = await IndexerApiClient(settings(), http).get_embedding(credentials())

    assert result.embedding_model == "bge-m3"
    assert result.values == (0.1, 0.2, 0.3)


@pytest.mark.asyncio
async def test_fail_sends_safe_failure_request_with_claim_credentials() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        assert_claim_headers(request)
        assert json.loads(request.content) == {
            "errorCode": "EMBEDDING_TIMEOUT",
            "safeErrorMessage": "Embedding service timed out",
            "retryable": True,
        }
        return httpx.Response(
            200,
            json={
                "jobId": 20,
                "status": "QUEUED",
                "availableAt": "2026-08-25T12:00:00Z",
                "existing": False,
            },
        )

    failure = FailureRequest(
        error_code="EMBEDDING_TIMEOUT",
        safe_error_message="Embedding service timed out",
        retryable=True,
    )
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        response = await IndexerApiClient(settings(), http).fail(credentials(), failure)

    assert response.job_id == 20
