import pytest
from pydantic import ValidationError

from ethnowear_indexer.api.models import (
    ClaimRequest,
    EmbeddingResponse,
    IndexingContentResponse,
    IndexingJobResponse,
    IndexResultRequest,
    KnowledgeChunkType,
    ProvenanceTrustState,
    TranscriptionApprovalState,
    WorkerJobType,
)


CONTENT_HASH = "a" * 64


def indexing_context() -> dict[str, object]:
    return {
        "jobId": 20,
        "knowledgeChunkId": 99,
        "content": "Одобрен текст за индексиране.",
        "contentHash": CONTENT_HASH,
        "language": "bg",
        "chunkType": "BOOK_EXCERPT",
        "documentId": 10,
        "sourceReferenceId": 30,
        "archiveItemId": None,
        "ontologyIri": None,
        "provenanceTrustState": "VERIFIED",
        "transcriptionApprovalState": "APPROVED",
    }


def test_claim_request_supports_only_index_chunk() -> None:
    request = ClaimRequest(worker_id="indexing-worker-1", lease_seconds=120)

    assert request.supported_job_types == (WorkerJobType.INDEX_CHUNK,)
    assert request.model_dump(by_alias=True, mode="json") == {
        "workerId": "indexing-worker-1",
        "supportedJobTypes": ["INDEX_CHUNK"],
        "leaseSeconds": 120,
    }


def test_indexing_context_maps_authoritative_chunk_metadata() -> None:
    context = IndexingContentResponse.model_validate(indexing_context())

    assert context.knowledge_chunk_id == 99
    assert context.chunk_type is KnowledgeChunkType.BOOK_EXCERPT
    assert context.provenance_trust_state is ProvenanceTrustState.VERIFIED
    assert (
        context.transcription_approval_state
        is TranscriptionApprovalState.APPROVED
    )


def test_indexing_context_rejects_invalid_content_hash() -> None:
    payload = indexing_context()
    payload["contentHash"] = "invalid"

    with pytest.raises(ValidationError, match="contentHash"):
        IndexingContentResponse.model_validate(payload)


def test_indexing_context_rejects_unknown_fields() -> None:
    payload = indexing_context()
    payload["storagePath"] = "/unsafe/path"

    with pytest.raises(ValidationError, match="Extra inputs are not permitted"):
        IndexingContentResponse.model_validate(payload)


def test_embedding_response_validates_server_vector() -> None:
    embedding = EmbeddingResponse.model_validate({
        "jobId": 20,
        "knowledgeChunkId": 99,
        "contentHash": CONTENT_HASH,
        "embeddingModel": "bge-m3",
        "embeddingDimensions": 3,
        "values": [0.1, 0.2, 0.3],
    })

    assert embedding.values == (0.1, 0.2, 0.3)


@pytest.mark.parametrize("values", ([0.1, 0.2], [0.1, float("nan"), 0.3]))
def test_embedding_response_rejects_invalid_vector(values: list[float]) -> None:
    with pytest.raises(ValidationError):
        EmbeddingResponse.model_validate({
            "jobId": 20,
            "knowledgeChunkId": 99,
            "contentHash": CONTENT_HASH,
            "embeddingModel": "bge-m3",
            "embeddingDimensions": 3,
            "values": values,
        })


def test_index_result_serializes_for_spring_contract() -> None:
    result = IndexResultRequest(
        content_hash=CONTENT_HASH,
        embedding_model="bge-m3",
        embedding_dimensions=1024,
        vector_collection="ethnowear_chunks_bge_m3_v1",
        vector_point_id="99",
    )

    assert result.model_dump(by_alias=True, mode="json") == {
        "contentHash": CONTENT_HASH,
        "embeddingModel": "bge-m3",
        "embeddingDimensions": 1024,
        "vectorCollection": "ethnowear_chunks_bge_m3_v1",
        "vectorPointId": "99",
    }


def test_index_result_rejects_invalid_dimensions() -> None:
    with pytest.raises(ValidationError, match="embedding_dimensions"):
        IndexResultRequest(
            content_hash=CONTENT_HASH,
            embedding_model="bge-m3",
            embedding_dimensions=0,
            vector_collection="collection",
            vector_point_id="99",
        )


def test_indexing_job_response_maps_idempotency_state() -> None:
    response = IndexingJobResponse.model_validate(
        {
            "jobId": 20,
            "knowledgeChunkId": 99,
            "existing": True,
        }
    )

    assert response.existing is True
