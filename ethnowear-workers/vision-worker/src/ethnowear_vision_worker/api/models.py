from enum import StrEnum

from pydantic import AliasChoices, Field

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
        WorkerJobType.VISION_OCR_ASSESSMENT,
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


class TranscriptionApprovalState(StrEnum):
    NOT_REQUIRED = "NOT_REQUIRED"
    PENDING = "PENDING"
    APPROVED = "APPROVED"
    REJECTED = "REJECTED"


class ReviewState(StrEnum):
    NOT_READY = "NOT_READY"
    REVIEW_REQUIRED = "REVIEW_REQUIRED"
    IN_REVIEW = "IN_REVIEW"
    APPROVED = "APPROVED"
    REJECTED = "REJECTED"


class IndexingState(StrEnum):
    NOT_ELIGIBLE = "NOT_ELIGIBLE"
    PENDING = "PENDING"
    INDEXED = "INDEXED"
    FAILED = "FAILED"
    OUTDATED = "OUTDATED"


class DeterministicQualityStatus(StrEnum):
    HIGH_QUALITY = "HIGH_QUALITY"
    MINOR_REVIEW = "MINOR_REVIEW"
    REVIEW_REQUIRED = "REVIEW_REQUIRED"
    POOR_QUALITY = "POOR_QUALITY"
    PROCESSING_FAILED = "PROCESSING_FAILED"
    INCOMPLETE = "INCOMPLETE"


class VisionQualitySignal(ApiModel):
    signal_type: str = Field(alias="type", min_length=1)
    decimal_value: float | None
    text_value: str | None
    severity: QualitySignalSeverity
    weight: float | None = Field(default=None, ge=0.0, le=1.0)
    safe_message: str | None


class VisionAssessmentContext(ApiModel):
    job_id: int = Field(gt=0)
    document_id: int = Field(gt=0)
    document_page_id: int = Field(gt=0)
    ocr_result_id: int = Field(gt=0)
    document_page_media_id: int = Field(gt=0)
    input_media_id: int = Field(gt=0)
    raw_ocr_text: str
    ocr_confidence: float | None = Field(default=None, ge=0.0, le=1.0)
    ocr_language: str | None
    structured_output_json: str | None
    deterministic_assessment_id: int = Field(gt=0)
    transcription_approval_state: TranscriptionApprovalState
    review_state: ReviewState
    indexing_state: IndexingState
    deterministic_quality_status: DeterministicQualityStatus
    deterministic_overall_score: float = Field(ge=0.0, le=1.0)
    deterministic_summary: str | None
    deterministic_limitations: str | None
    deterministic_signals: tuple[VisionQualitySignal, ...]
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
    assessor_name: str = Field(min_length=1)
    assessor_version: str = Field(min_length=1)
    score_version: str = Field(min_length=1)
    overall_score: float = Field(ge=0.0, le=1.0)
    quality_status: QualityStatus
    summary: str | None = None
    limitations: str | None = None
    signals: tuple[QualitySignalRequest, ...] = Field(min_length=1)


class VisionIssueRequest(ApiModel):
    issue_type: str = Field(
        min_length=1,
        validation_alias=AliasChoices("issueType", "code"),
    )
    explanation_bg: str = Field(
        min_length=1,
        validation_alias=AliasChoices(
            "explanationBg",
            "safeMessage",
            "safe_message",
        ),
    )
    confidence: float = Field(ge=0.0, le=1.0)
    original_text: str | None = None
    original_context: str | None = None
    suggested_text: str | None = None
    suggested_context: str | None = None
    start_offset: int | None = Field(default=None, ge=0)
    end_offset: int | None = Field(default=None, gt=0)
    safely_applicable: bool = False

    @property
    def code(self) -> str:
        return self.issue_type

    @property
    def safe_message(self) -> str:
        return self.explanation_bg

    @property
    def context_text(self) -> str | None:
        return self.original_context


class VisionUncertainPassageRequest(ApiModel):
    excerpt: str = Field(min_length=1)
    reason: str = Field(min_length=1)
    confidence: float = Field(ge=0.0, le=1.0)


class VisionAssessmentRequest(ApiModel):
    assessment: QualityAssessmentRequest
    requires_review: bool
    suggested_text: str = Field(min_length=1)
    model_name: str = Field(min_length=1)
    model_version: str = Field(min_length=1)
    prompt_version: str = Field(min_length=1)
    issues: tuple[VisionIssueRequest, ...]
    uncertain_passages: tuple[VisionUncertainPassageRequest, ...]


class VisionAssessmentResponse(ApiModel):
    assessment_id: int = Field(gt=0)
    suggestion_id: int = Field(gt=0)
    job_id: int = Field(gt=0)
    document_id: int = Field(gt=0)
    page_id: int = Field(gt=0)
    ocr_result_id: int = Field(gt=0)
    input_media_id: int = Field(gt=0)
    existing: bool


__all__ = [
    "CancellationResponse",
    "ClaimRequest",
    "ClaimResponse",
    "CompletionResponse",
    "DeterministicQualityStatus",
    "FailureRequest",
    "FailureResponse",
    "HeartbeatRequest",
    "HeartbeatResponse",
    "IndexingState",
    "JobStatus",
    "JobTarget",
    "QualityAssessmentRequest",
    "QualitySignalRequest",
    "QualitySignalSeverity",
    "QualityStatus",
    "ReadinessResponse",
    "ResourceLimits",
    "ReviewState",
    "TranscriptionApprovalState",
    "VisionAssessmentContext",
    "VisionAssessmentRequest",
    "VisionAssessmentResponse",
    "VisionIssueRequest",
    "VisionQualitySignal",
    "VisionUncertainPassageRequest",
    "WorkerJobType",
]
