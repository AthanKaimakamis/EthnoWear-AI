from datetime import datetime
from enum import StrEnum
from typing import Literal, Self

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    SecretStr,
    model_validator
)


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
    supported_job_types: tuple[WorkerJobType, ...] = (
        WorkerJobType.PAGE_EXTRACTION,
    )
    lease_seconds: int | None = Field(default=None, gt=0)


class JobTarget(ApiModel):
    document_id: int | None
    document_page_id: int | None
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
        return self


class HeartbeatRequest(ApiModel):
    lease_seconds: int | None = Field(default=None, gt=0)


class HeartbeatResponse(ApiModel):
    lease_expires_at: datetime
    cancellation_requested: bool


class ManifestPageRequest(ApiModel):
    pdf_page_index: int = Field(ge=0)
    page_sequence: int = Field(gt=0)


class ManifestRequest(ApiModel):
    total_page_count: int = Field(gt=0)
    pages: tuple[ManifestPageRequest, ...] = Field(min_length=1)

    @model_validator(mode="after")
    def validate_page_identities(self) -> Self:
        if len(self.pages) != self.total_page_count:
            raise ValueError("Manifest page count does not match its entries")

        for index, page in enumerate(self.pages):
            if page.pdf_page_index != index or page.page_sequence != index + 1:
                raise ValueError("Manifest pages must be contiguous")

        return self


class ManifestPageResponse(ApiModel):
    page_id: int = Field(gt=0)
    pdf_page_index: int = Field(ge=0)
    page_sequence: int = Field(gt=0)
    rendition_required: bool


class ManifestResponse(ApiModel):
    document_id: int = Field(gt=0)
    page_count: int = Field(gt=0)
    pages: tuple[ManifestPageResponse, ...] = Field(min_length=1)


class RenditionType(StrEnum):
    PDF_PAGE_RENDER = "PDF_PAGE_RENDER"


class ColorMode(StrEnum):
    RGB = "RGB"
    GRAYSCALE = "GRAYSCALE"


class RenditionMetadata(ApiModel):
    pdf_page_index: int = Field(ge=0)
    page_sequence: int = Field(gt=0)
    rendition_type: RenditionType
    dpi: int = Field(gt=0)
    color_mode: ColorMode
    pixel_width: int = Field(gt=0)
    pixel_height: int = Field(gt=0)
    renderer_name: str = Field(min_length=1, max_length=100)
    renderer_version: str | None = Field(default=None, max_length=100)


class RenditionResponse(ApiModel):
    page_id: int = Field(gt=0)
    page_media_id: int = Field(gt=0)
    media_asset_id: int = Field(gt=0)
    rendition_type: RenditionType
    existing: bool


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


class CompletionResponse(ApiModel):
    job_id: int = Field(gt=0)
    document_id: int = Field(gt=0)
    page_count: int = Field(gt=0)
    queued_ocr_jobs: int = Field(ge=0)
    existing: bool


class FailureRequest(ApiModel):
    error_code: str = Field(
        min_length=1,
        max_length=100,
        pattern=r"^[A-Z][A-Z0-9_]*$",
    )
    safe_error_message: str = Field(
        min_length=1,
        max_length=1000,
    )
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
