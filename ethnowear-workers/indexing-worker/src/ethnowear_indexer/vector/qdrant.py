from qdrant_client import AsyncQdrantClient
from qdrant_client.models import Distance, PointStruct, VectorParams

from ethnowear_indexer.api.models import IndexingContentResponse
from ethnowear_indexer.config import IndexerSettings
from ethnowear_worker_common.api.errors import WorkerApiContractError


class QdrantVectorStore:
    def __init__(self, settings: IndexerSettings, client: AsyncQdrantClient) -> None:
        self._collection = settings.qdrant_collection
        self._client = client

    async def health(self) -> None:
        await self._client.get_collections()

    async def ensure_collection(self, dimensions: int) -> None:
        if dimensions <= 0:
            raise ValueError("Embedding dimensions must be positive")

        if await self._client.collection_exists(self._collection):
            collection = await self._client.get_collection(self._collection)
            vectors = collection.config.params.vectors

            if not isinstance(vectors, VectorParams):
                raise WorkerApiContractError("Qdrant collection has an unsupported vector configuration")

            if vectors.size != dimensions:
                raise WorkerApiContractError("Qdrant collection has an unexpected vector dimension")

            if vectors.distance is not Distance.COSINE:
                raise WorkerApiContractError("Qdrant collection has an unexpected distance metric")

            return

        await self._client.create_collection(
            collection_name=self._collection,
            vectors_config=VectorParams(
                size=dimensions,
                distance=Distance.COSINE,
            )
        )

    async def upsert(
            self,
            context: IndexingContentResponse,
            vector: list[float],
            dimensions: int,
    ) -> str:
        if len(vector) != dimensions:
            raise ValueError("Embedding vector has an unexpected dimension")

        point_id = context.knowledge_chunk_id

        await self._client.upsert(
            collection_name=self._collection,
            wait=True,
            points=[
                PointStruct(
                    id=point_id,
                    vector=vector,
                    payload={
                        "knowledgeChunkId": context.knowledge_chunk_id,
                        "content": context.content,
                        "contentHash": context.content_hash,
                        "language": context.language,
                        "chunkType": context.chunk_type.value,
                        "documentId": context.document_id,
                        "sourceReferenceId": context.source_reference_id,
                        "archiveItemId": context.archive_item_id,
                        "ontologyIri": context.ontology_iri,
                        "provenanceTrustState": context.provenance_trust_state.value,
                        "transcriptionApprovalState": context.transcription_approval_state.value,
                    },
                )
            ]
        )

        return str(point_id)
