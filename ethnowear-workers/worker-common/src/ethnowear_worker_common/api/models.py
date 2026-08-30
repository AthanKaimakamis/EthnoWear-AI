from datetime import datetime
from enum import StrEnum
from typing import Literal, Self

from pydantic import BaseModel, ConfigDict, Field, SecretStr, model_validator


def to_camel(name: str) -> str:
    first, *rest = name.split("_")
    return first + "".join(part.capitalize() for part in rest)


class ApiModel(BaseModel):
    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
        extra="forbid",
    )


class WorkerJobType(StrEnum):
    PAGE_EXTRACTION = "PAGE_EXTRACTION"
    OCR = "OCR"
    EXTRACT_PAGE_FIGURES = "EXTRACT_PAGE_FIGURES"
    OCR_QUALITY_ASSESSMENT = "OCR_QUALITY_ASSESSMENT"
    VISION_OCR_ASSESSMENT = "VISION_OCR_ASSESSMENT"
    INDEX_CHUNK = "INDEX_CHUNK"


class JobStatus(StrEnum):
    QUEUED = "QUEUED"
    CLAIMED = "CLAIMED"
    RUNNING = "RUNNING"
    SUCCEEDED = "SUCCEEDED"
    FAILED = "FAILED"
    RETRY_WAIT = "RETRY_WAIT"
    CANCEL_REQUESTED = "CANCEL_REQUESTED"
    CANCELLED = "CANCELLED"
    TIMED_OUT = "TIMED_OUT"
    DEAD = "DEAD"


class ReadinessResponse(ApiModel):
    status: Literal["READY"]
    worker_api_enabled: bool

    @model_validator(mode="after")
    def validate_enabled(self) -> Self:
        if not self.worker_api_enabled:
            raise ValueError("Worker API readiness response is not enabled")
        return self


class ClaimRequest(ApiModel):
    worker_id: str = Field(
        min_length=1,
        max_length=150,
        pattern=r"^[A-Za-z0-9][A-Za-z0-9._-]*$",
    )
    supported_job_types: tuple[WorkerJobType, ...] = Field(min_length=1)
    lease_seconds: int | None = Field(default=None, gt=0)


class JobTarget(ApiModel):
    document_id: int | None
    document_page_id: int | None
    pdf_page_index: int | None = Field(default=None, ge=0)
    knowledge_chunk_id: int | None
    input_available: bool


class ResourceLimits(ApiModel):
    maximum_input_bytes: int = Field(gt=0)
    maximum_page_count: int = Field(gt=0)
    render_dpi: int = Field(gt=0)
    maximum_pixel_width: int = Field(gt=0)
    maximum_pixel_height: int = Field(gt=0)
    maximum_page_pixels: int = Field(gt=0)
    maximum_rendition_bytes: int = Field(gt=0)
    maximum_ocr_text_characters: int = Field(gt=0)
    maximum_ocr_output_bytes: int = Field(gt=0)
    maximum_ocr_context_bytes: int = Field(gt=0)
    maximum_quality_assessment_payload_bytes: int = Field(gt=0)
    maximum_quality_signals: int = Field(gt=0)
    maximum_quality_signal_type_characters: int = Field(gt=0)
    maximum_quality_signal_text_characters: int = Field(gt=0)
    maximum_quality_summary_characters: int = Field(gt=0)
    maximum_quality_limitations_characters: int = Field(gt=0)
    maximum_quality_message_characters: int = Field(gt=0)
    maximum_vision_assessment_payload_bytes: int = Field(gt=0)
    maximum_vision_suggestion_characters: int = Field(gt=0)
    maximum_vision_issues_json_characters: int = Field(gt=0)
    maximum_vision_issues: int = Field(gt=0)
    maximum_vision_uncertain_passages: int = Field(gt=0)
    maximum_vision_issue_code_characters: int = Field(gt=0)
    maximum_vision_excerpt_characters: int = Field(gt=0)
    maximum_vision_reason_characters: int = Field(gt=0)
    maximum_vision_model_name_characters: int = Field(gt=0)
    maximum_vision_model_version_characters: int = Field(gt=0)
    maximum_vision_prompt_version_characters: int = Field(gt=0)
    maximum_indexing_content_characters: int = Field(gt=0)
    maximum_embedding_dimensions: int = Field(gt=0)
    maximum_embedding_model_characters: int = Field(gt=0)
    maximum_vector_collection_characters: int = Field(gt=0)
    maximum_vector_point_id_characters: int = Field(gt=0)
    maximum_figure_candidates: int = Field(gt=0)
    maximum_figure_caption_characters: int = Field(gt=0)
    maximum_printed_figure_number_characters: int = Field(gt=0)
    maximum_figure_crop_bytes: int = Field(gt=0)
    job_timeout_seconds: int = Field(gt=0)
    heartbeat_interval_seconds: int = Field(gt=0)
    maximum_lease_seconds: int = Field(gt=0)


class ClaimResponse(ApiModel):
    job_id: int = Field(gt=0)
    job_type: WorkerJobType
    claim_token: SecretStr = Field(min_length=1)
    attempt: int = Field(gt=0)
    claimed_at: datetime
    lease_expires_at: datetime
    target: JobTarget
    limits: ResourceLimits

    @model_validator(mode="after")
    def validate_claim_contract(self) -> Self:
        if self.lease_expires_at <= self.claimed_at:
            raise ValueError("Claim lease must expire after it is issued")

        if self.limits.heartbeat_interval_seconds >= self.limits.maximum_lease_seconds:
            raise ValueError("Heartbeat interval must be shorter than maximum lease")

        if self.job_type == WorkerJobType.PAGE_EXTRACTION:
            if self.target.document_id is None or not self.target.input_available:
                raise ValueError("PAGE_EXTRACTION requires a document PDF input")

        if self.job_type in {
            WorkerJobType.OCR,
            WorkerJobType.EXTRACT_PAGE_FIGURES,
            WorkerJobType.OCR_QUALITY_ASSESSMENT,
            WorkerJobType.VISION_OCR_ASSESSMENT,
        }:
            if (
                self.target.document_id is None
                or self.target.document_page_id is None
                or not self.target.input_available
            ):
                raise ValueError(f"{self.job_type.value} requires a document page image input")

        if (
            self.job_type == WorkerJobType.INDEX_CHUNK
            and self.target.knowledge_chunk_id is None
        ):
            raise ValueError("INDEX_CHUNK requires a knowledge chunk target")

        return self


class HeartbeatRequest(ApiModel):
    lease_seconds: int | None = Field(default=None, gt=0)


class HeartbeatResponse(ApiModel):
    lease_expires_at: datetime
    cancellation_requested: bool


class CompletionResponse(ApiModel):
    job_id: int = Field(gt=0)
    job_type: WorkerJobType
    document_id: int | None
    document_page_id: int | None
    page_count: int = Field(ge=0)
    queued_ocr_jobs: int = Field(ge=0)
    queued_quality_assessment_jobs: int = Field(ge=0)
    queued_vision_assessment_jobs: int = Field(ge=0)
    existing: bool


class FailureRequest(ApiModel):
    error_code: str = Field(
        min_length=1,
        max_length=100,
        pattern=r"^[A-Z][A-Z0-9_]*$",
    )
    safe_error_message: str = Field(min_length=1, max_length=1000)
    retryable: bool


class FailureResponse(ApiModel):
    job_id: int = Field(gt=0)
    status: JobStatus
    available_at: datetime | None
    existing: bool


class CancellationResponse(ApiModel):
    job_id: int = Field(gt=0)
    status: JobStatus
    finished_at: datetime
    existing: bool
