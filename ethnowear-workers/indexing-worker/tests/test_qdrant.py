from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest
from qdrant_client.models import Distance, VectorParams

from ethnowear_indexer.api.models import IndexingContentResponse
from ethnowear_indexer.config import IndexerSettings
from ethnowear_indexer.vector.qdrant import QdrantVectorStore
from ethnowear_worker_common.api.errors import WorkerApiContractError


def settings() -> IndexerSettings:
    return IndexerSettings(
        api_base_url="http://api:8080",
        worker_id="indexer-1",
        api_token="x" * 32,
        qdrant_url="http://qdrant:6333",
        qdrant_collection="chunks",
    )


def context() -> IndexingContentResponse:
    return IndexingContentResponse.model_validate(
        {
            "jobId": 20,
            "knowledgeChunkId": 99,
            "content": "Одобрен текст.",
            "contentHash": "a" * 64,
            "language": "bg",
            "chunkType": "BOOK_EXCERPT",
            "documentId": 10,
            "sourceReferenceId": 30,
            "archiveItemId": None,
            "ontologyIri": None,
            "provenanceTrustState": "VERIFIED",
            "transcriptionApprovalState": "APPROVED",
        }
    )


@pytest.mark.asyncio
async def test_ensure_collection_creates_cosine_collection() -> None:
    client = AsyncMock()
    client.collection_exists.return_value = False

    await QdrantVectorStore(settings(), client).ensure_collection(3)

    client.create_collection.assert_awaited_once()
    arguments = client.create_collection.await_args.kwargs
    assert arguments["collection_name"] == "chunks"
    assert arguments["vectors_config"].size == 3
    assert arguments["vectors_config"].distance is Distance.COSINE


@pytest.mark.asyncio
async def test_ensure_collection_accepts_matching_existing_collection() -> None:
    client = AsyncMock()
    client.collection_exists.return_value = True
    client.get_collection.return_value = SimpleNamespace(
        config=SimpleNamespace(
            params=SimpleNamespace(
                vectors=VectorParams(size=3, distance=Distance.COSINE)
            )
        )
    )

    await QdrantVectorStore(settings(), client).ensure_collection(3)

    client.create_collection.assert_not_awaited()


@pytest.mark.asyncio
async def test_ensure_collection_rejects_wrong_dimensions() -> None:
    client = AsyncMock()
    client.collection_exists.return_value = True
    client.get_collection.return_value = SimpleNamespace(
        config=SimpleNamespace(
            params=SimpleNamespace(
                vectors=VectorParams(size=4, distance=Distance.COSINE)
            )
        )
    )

    with pytest.raises(WorkerApiContractError, match="dimension"):
        await QdrantVectorStore(settings(), client).ensure_collection(3)


@pytest.mark.asyncio
async def test_upsert_uses_stable_chunk_id_and_metadata() -> None:
    client = AsyncMock()

    point_id = await QdrantVectorStore(settings(), client).upsert(
        context(), [0.1, 0.2, 0.3], 3
    )

    assert point_id == "99"
    arguments = client.upsert.await_args.kwargs
    assert arguments["collection_name"] == "chunks"
    assert arguments["wait"] is True
    point = arguments["points"][0]
    assert point.id == 99
    assert point.vector == [0.1, 0.2, 0.3]
    assert point.payload["content"] == "Одобрен текст."
    assert point.payload["contentHash"] == "a" * 64
    assert point.payload["transcriptionApprovalState"] == "APPROVED"


@pytest.mark.asyncio
async def test_upsert_rejects_wrong_vector_dimensions() -> None:
    client = AsyncMock()

    with pytest.raises(ValueError, match="dimension"):
        await QdrantVectorStore(settings(), client).upsert(context(), [0.1], 3)

    client.upsert.assert_not_awaited()
