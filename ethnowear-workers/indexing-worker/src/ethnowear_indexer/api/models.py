from enum import StrEnum
import math

from pydantic import Field, model_validator

from ethnowear_worker_common.api.models import (
    ApiModel,
    CancellationResponse,
    ClaimRequest as CommonClaimRequest,
    ClaimResponse,
    CompletionResponse,
    FailureRequest,
    FailureResponse,
    HeartbeatRequest,
    HeartbeatResponse,
    JobStatus,
    JobTarget,
    ReadinessResponse,
    ResourceLimits,
    WorkerJobType,
)


class ClaimRequest(CommonClaimRequest):
    supported_job_types: tuple[WorkerJobType, ...] = (
        WorkerJobType.INDEX_CHUNK,
    )


class KnowledgeChunkType(StrEnum):
    GENERAL = "GENERAL"
    REGION = "REGION"
    ORNAMENT = "ORNAMENT"
    TECHNIQUE = "TECHNIQUE"
    MOTIF = "MOTIF"
    COLOR = "COLOR"
    REGIONAL_EMBROIDERY = "REGIONAL_EMBROIDERY"
    SOURCE_EXCERPT = "SOURCE_EXCERPT"
    BOOK_EXCERPT = "BOOK_EXCERPT"
    STANDALONE_EVIDENCE = "STANDALONE_EVIDENCE"


class ProvenanceTrustState(StrEnum):
    UNKNOWN = "UNKNOWN"
    UNTRUSTED = "UNTRUSTED"
    PARTIAL = "PARTIAL"
    TRUSTED = "TRUSTED"
    VERIFIED = "VERIFIED"


class TranscriptionApprovalState(StrEnum):
    NOT_REQUIRED = "NOT_REQUIRED"
    PENDING = "PENDING"
    APPROVED = "APPROVED"
    REJECTED = "REJECTED"


class IndexingContentResponse(ApiModel):
    job_id: int = Field(gt=0)
    knowledge_chunk_id: int = Field(gt=0)
    content: str
    content_hash: str = Field(pattern=r"^[0-9a-f]{64}$")
    language: str
    chunk_type: KnowledgeChunkType
    document_id: int | None
    source_reference_id: int | None
    archive_item_id: int | None
    ontology_iri: str | None
    provenance_trust_state: ProvenanceTrustState
    transcription_approval_state: TranscriptionApprovalState


class EmbeddingResponse(ApiModel):
    job_id: int = Field(gt=0)
    knowledge_chunk_id: int = Field(gt=0)
    content_hash: str = Field(pattern=r"^[0-9a-f]{64}$")
    embedding_model: str = Field(min_length=1, max_length=100)
    embedding_dimensions: int = Field(gt=0)
    values: tuple[float, ...] = Field(min_length=1)

    @model_validator(mode="after")
    def validate_vector(self) -> "EmbeddingResponse":
        if len(self.values) != self.embedding_dimensions:
            raise ValueError("Embedding vector length does not match its dimensions")
        if any(not math.isfinite(value) for value in self.values):
            raise ValueError("Embedding vector contains a non-finite value")
        return self


class IndexResultRequest(ApiModel):
    content_hash: str = Field(pattern=r"^[0-9a-f]{64}$")
    embedding_model: str = Field(min_length=1, max_length=100)
    embedding_dimensions: int = Field(gt=0)
    vector_collection: str = Field(min_length=1, max_length=150)
    vector_point_id: str = Field(min_length=1, max_length=255)


class IndexingJobResponse(ApiModel):
    job_id: int = Field(gt=0)
    knowledge_chunk_id: int = Field(gt=0)
    existing: bool
