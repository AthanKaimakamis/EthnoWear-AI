import hashlib
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest
from pydantic import SecretStr

from ethnowear_indexer.api.models import (
    IndexingContentResponse,
    EmbeddingResponse,
    IndexingJobResponse,
    WorkerJobType,
)
from ethnowear_indexer.config import IndexerSettings
from ethnowear_indexer.jobs.processor import (
    IndexChunkOperation,
    IndexChunkProcessor,
)
from ethnowear_worker_common.api.errors import WorkerApiContractError
from ethnowear_worker_common.jobs.context import ClaimCredentials


CONTENT = "Одобрен текст за индексиране."
CONTENT_HASH = hashlib.sha256(CONTENT.encode("utf-8")).hexdigest()


def settings() -> IndexerSettings:
    return IndexerSettings(
        api_base_url="http://api:8080",
        worker_id="indexer-1",
        api_token="x" * 32,
        qdrant_url="http://qdrant:6333",
        qdrant_collection="chunks",
    )


def claim() -> SimpleNamespace:
    return SimpleNamespace(
        job_id=20,
        job_type=WorkerJobType.INDEX_CHUNK,
        target=SimpleNamespace(knowledge_chunk_id=99),
        limits=SimpleNamespace(
            maximum_embedding_dimensions=3,
            maximum_indexing_content_characters=1000,
        ),
    )


def context(**changes: object) -> IndexingContentResponse:
    payload: dict[str, object] = {
        "jobId": 20,
        "knowledgeChunkId": 99,
        "content": CONTENT,
        "contentHash": CONTENT_HASH,
        "language": "bg",
        "chunkType": "BOOK_EXCERPT",
        "documentId": 10,
        "sourceReferenceId": None,
        "archiveItemId": None,
        "ontologyIri": None,
        "provenanceTrustState": "VERIFIED",
        "transcriptionApprovalState": "APPROVED",
    }
    payload.update(changes)
    return IndexingContentResponse.model_validate(payload)


def operation(
        api_client: AsyncMock,
        vector_store: AsyncMock,
) -> IndexChunkOperation:
    return IndexChunkOperation(
        api_client=api_client,
        vector_store=vector_store,
        settings=settings(),
    )


@pytest.mark.asyncio
async def test_run_indexes_and_reports_result() -> None:
    api_client = AsyncMock()
    api_client.get_indexing_context.return_value = context()
    api_client.get_embedding.return_value = EmbeddingResponse(
        job_id=20,
        knowledge_chunk_id=99,
        content_hash=CONTENT_HASH,
        embedding_model="bge-m3",
        embedding_dimensions=3,
        values=(0.1, 0.2, 0.3),
    )
    api_client.submit_index_result.return_value = IndexingJobResponse(
        job_id=20,
        knowledge_chunk_id=99,
        existing=False,
    )
    vector_store = AsyncMock()
    vector_store.upsert.return_value = "99"
    credentials = ClaimCredentials(20, SecretStr("claim-token"))

    await operation(api_client, vector_store).run(
        claim(), credentials
    )

    api_client.get_embedding.assert_awaited_once_with(credentials)
    vector_store.ensure_collection.assert_awaited_once_with(3)
    vector_store.upsert.assert_awaited_once()
    submitted = api_client.submit_index_result.await_args.args[1]
    assert submitted.content_hash == CONTENT_HASH
    assert submitted.embedding_model == "bge-m3"
    assert submitted.embedding_dimensions == 3
    assert submitted.vector_collection == "chunks"
    assert submitted.vector_point_id == "99"


@pytest.mark.asyncio
async def test_run_rejects_stale_content_before_embedding() -> None:
    api_client = AsyncMock()
    api_client.get_indexing_context.return_value = context(contentHash="b" * 64)
    vector_store = AsyncMock()

    with pytest.raises(WorkerApiContractError, match="hash"):
        await operation(api_client, vector_store).run(
            claim(), ClaimCredentials(20, SecretStr("claim-token"))
        )

    api_client.get_embedding.assert_not_awaited()
    vector_store.upsert.assert_not_awaited()


@pytest.mark.asyncio
async def test_run_rejects_unapproved_content_before_embedding() -> None:
    api_client = AsyncMock()
    api_client.get_indexing_context.return_value = context(
        transcriptionApprovalState="PENDING"
    )
    vector_store = AsyncMock()

    with pytest.raises(WorkerApiContractError, match="not approved"):
        await operation(api_client, vector_store).run(
            claim(), ClaimCredentials(20, SecretStr("claim-token"))
        )

    api_client.get_embedding.assert_not_awaited()


@pytest.mark.asyncio
async def test_run_rejects_stale_server_embedding_before_qdrant() -> None:
    api_client = AsyncMock()
    api_client.get_indexing_context.return_value = context()
    api_client.get_embedding.return_value = EmbeddingResponse(
        job_id=20,
        knowledge_chunk_id=99,
        content_hash="b" * 64,
        embedding_model="bge-m3",
        embedding_dimensions=3,
        values=(0.1, 0.2, 0.3),
    )
    vector_store = AsyncMock()

    with pytest.raises(WorkerApiContractError, match="stale"):
        await operation(api_client, vector_store).run(
            claim(), ClaimCredentials(20, SecretStr("claim-token"))
        )

    vector_store.ensure_collection.assert_not_awaited()
    vector_store.upsert.assert_not_awaited()


@pytest.mark.asyncio
async def test_processor_runs_operation_through_shared_lifecycle(
        monkeypatch: pytest.MonkeyPatch,
) -> None:
    api_client = AsyncMock()
    index_operation = AsyncMock()
    execute = AsyncMock()
    monkeypatch.setattr(
        "ethnowear_indexer.jobs.processor.execute_claimed_job",
        execute,
    )
    job = claim()
    processor = IndexChunkProcessor(
        api_client=api_client,
        operation=index_operation,
    )

    await processor.process(job)

    assert execute.await_args.kwargs["api_client"] is api_client
    assert execute.await_args.kwargs["claim"] is job

    credentials = ClaimCredentials(20, SecretStr("claim-token"))
    await execute.await_args.kwargs["operation"](credentials)
    index_operation.run.assert_awaited_once_with(job, credentials)
