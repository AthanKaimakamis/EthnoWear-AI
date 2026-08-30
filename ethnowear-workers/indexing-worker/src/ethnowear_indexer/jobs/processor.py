import hashlib
from typing import Protocol

from ethnowear_indexer.api.client import IndexerApiClient
from ethnowear_indexer.api.models import (
    ClaimResponse,
    EmbeddingResponse,
    IndexResultRequest,
    IndexingContentResponse,
    ProvenanceTrustState,
    TranscriptionApprovalState,
    WorkerJobType
)
from ethnowear_indexer.jobs.execution import execute_claimed_job
from ethnowear_indexer.config import IndexerSettings
from ethnowear_worker_common.api.errors import WorkerApiContractError
from ethnowear_worker_common.jobs.context import ClaimCredentials


class VectorStore(Protocol):
    async def ensure_collection(self, dimensions: int) -> None:
        ...

    async def upsert(
            self,
            context: IndexingContentResponse,
            vector: list[float],
            dimensions: int,
    ) -> str:
        ...


class IndexChunkOperation:
    def __init__(
            self,
            *,
            api_client: IndexerApiClient,
            vector_store: VectorStore,
            settings: IndexerSettings,
    ) -> None:
        self._api_client = api_client
        self._vector_store = vector_store
        self._settings = settings

    async def run(self, claim: ClaimResponse, credentials: ClaimCredentials) -> None:
        self._validate_claim(claim)

        context = await self._api_client.get_indexing_context(credentials)
        self._validate_context(claim, context)

        embedding = await self._api_client.get_embedding(credentials)
        self._validate_embedding(claim, context, embedding)

        await self._vector_store.ensure_collection(
            embedding.embedding_dimensions
        )
        point_id = await self._vector_store.upsert(
            context,
            list(embedding.values),
            embedding.embedding_dimensions,
        )

        result = IndexResultRequest(
            content_hash=context.content_hash,
            embedding_model=embedding.embedding_model,
            embedding_dimensions=embedding.embedding_dimensions,
            vector_collection=self._settings.qdrant_collection,
            vector_point_id=point_id,
        )

        accepted = await self._api_client.submit_index_result(credentials, result)

        if accepted.job_id != claim.job_id:
            raise WorkerApiContractError("Index result response identifies a different job")

        if accepted.knowledge_chunk_id != context.knowledge_chunk_id:
            raise WorkerApiContractError("Index result response identifies a different chunk")

    @staticmethod
    def _validate_claim(claim: ClaimResponse) -> None:
        if claim.job_type is not WorkerJobType.INDEX_CHUNK:
            raise ValueError("Index operation requires an INDEX_CHUNK job")

        if claim.target.knowledge_chunk_id is None:
            raise ValueError("INDEX_CHUNK claim has no knowledge chunk target")

    @staticmethod
    def _validate_context(claim: ClaimResponse, context: IndexingContentResponse) -> None:
        if context.job_id != claim.job_id:
            raise WorkerApiContractError("Indexing context identifies a different job")

        if context.knowledge_chunk_id != claim.target.knowledge_chunk_id:
            raise WorkerApiContractError("Indexing context identifies a different chunk")

        if not context.content.strip():
            raise WorkerApiContractError("Indexing context contains empty content")

        if len(context.content) > claim.limits.maximum_indexing_content_characters:
            raise WorkerApiContractError("Indexing content exceeds the backed limit")

        calculated_hash = hashlib.sha256(
            context.content.encode("utf-8")
        ).hexdigest()

        if calculated_hash != context.content_hash:
            raise WorkerApiContractError("Indexing content hash does not match its content")

        if context.transcription_approval_state is not TranscriptionApprovalState.APPROVED:
            raise WorkerApiContractError("Indexing content transcription is not approved")

        if context.provenance_trust_state not in {
            ProvenanceTrustState.TRUSTED,
            ProvenanceTrustState.VERIFIED,
        }:
            raise WorkerApiContractError("Indexing content provenance is not trusted")

    @staticmethod
    def _validate_embedding(
            claim: ClaimResponse,
            context: IndexingContentResponse,
            embedding: EmbeddingResponse,
    ) -> None:
        if embedding.job_id != claim.job_id:
            raise WorkerApiContractError("Embedding identifies a different job")
        if embedding.knowledge_chunk_id != context.knowledge_chunk_id:
            raise WorkerApiContractError("Embedding identifies a different chunk")
        if embedding.content_hash != context.content_hash:
            raise WorkerApiContractError("Embedding content hash is stale")
        if embedding.embedding_dimensions > claim.limits.maximum_embedding_dimensions:
            raise WorkerApiContractError("Embedding exceeds the backend limit")


class IndexChunkProcessor:
    def __init__(
            self,
            *,
            api_client: IndexerApiClient,
            operation: IndexChunkOperation,
    ) -> None:
        self._api_client = api_client
        self._operation = operation

    async def process(self, claim: ClaimResponse) -> None:
        async def run_operation(credentials: ClaimCredentials) -> None:
            await self._operation.run(claim, credentials)

        await execute_claimed_job(
            api_client=self._api_client,
            claim=claim,
            operation=run_operation,
        )
