from enum import StrEnum

from pydantic import Field

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
        WorkerJobType.OCR_QUALITY_ASSESSMENT,
    )


class QualityStatus(StrEnum):
    PASS = "PASS"
    WARNING = "WARNING"
    FAIL = "FAIL"
    REVIEW_REQUIRED = "REVIEW_REQUIRED"


class QualitySignalSeverity(StrEnum):
    INFO = "INFO"
    WARNING = "WARNING"
    ERROR = "ERROR"


class QualityAssessmentContextResponse(ApiModel):
    job_id: int = Field(gt=0)
    document_id: int = Field(gt=0)
    page_id: int = Field(gt=0)
    ocr_result_id: int = Field(gt=0)
    page_media_id: int = Field(gt=0)
    input_media_id: int = Field(gt=0)
    raw_ocr_text: str
    ocr_confidence: float | None = Field(default=None, ge=0.0, le=1.0)
    ocr_language: str | None
    structured_output_json: str | None
    image_mime_type: str = Field(min_length=1)
    image_size_bytes: int = Field(gt=0)
    image_width: int | None = Field(default=None, gt=0)
    image_height: int | None = Field(default=None, gt=0)
    image_dpi: int | None = Field(default=None, gt=0)
    image_color_mode: str | None


class QualitySignalRequest(ApiModel):
    signal_type: str = Field(alias="type", min_length=1)
    decimal_value: float | None = None
    text_value: str | None = None
    severity: QualitySignalSeverity
    weight: float | None = Field(default=None, ge=0.0, le=1.0)
    safe_message: str | None = None


class QualityAssessmentRequest(ApiModel):
    assessor_name: str = Field(min_length=1, max_length=100)
    assessor_version: str = Field(min_length=1, max_length=100)
    score_version: str = Field(min_length=1, max_length=50)
    overall_score: float = Field(ge=0.0, le=1.0)
    quality_status: QualityStatus
    summary: str | None = None
    limitations: str | None = None
    signals: tuple[QualitySignalRequest, ...] = Field(min_length=1)


class QualityAssessmentResponse(ApiModel):
    assessment_id: int = Field(gt=0)
    job_id: int = Field(gt=0)
    document_id: int = Field(gt=0)
    page_id: int = Field(gt=0)
    ocr_result_id: int = Field(gt=0)
    input_media_id: int = Field(gt=0)
    existing: bool
