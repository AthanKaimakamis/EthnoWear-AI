from typing import Self

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
        WorkerJobType.EXTRACT_PAGE_FIGURES,
    )


class FigureCandidateResponse(ApiModel):
    candidate_id: int = Field(gt=0)
    candidate_ordinal: int = Field(gt=0)
    normalized_x: float = Field(ge=0.0, le=1.0)
    normalized_y: float = Field(ge=0.0, le=1.0)
    normalized_width: float = Field(gt=0.0, le=1.0)
    normalized_height: float = Field(gt=0.0, le=1.0)
    raw_caption_text: str | None = None
    detection_confidence: float | None = Field(
        default=None,
        ge=0.0,
        le=1.0,
    )

    @model_validator(mode="after")
    def validate_bounds(self) -> Self:
        if self.normalized_x + self.normalized_width > 1.0:
            raise ValueError("Figure candidate exceeds the page width")

        if self.normalized_y + self.normalized_height > 1.0:
            raise ValueError("Figure candidate exceeds the page height")

        return self


class FigureExtractionContextResponse(ApiModel):
    job_id: int = Field(gt=0)
    document_id: int = Field(gt=0)
    document_page_id: int = Field(gt=0)
    document_page_media_id: int = Field(gt=0)
    input_media_asset_id: int = Field(gt=0)
    ocr_result_id: int = Field(gt=0)
    ocr_layout_json: str | None
    candidates: tuple[FigureCandidateResponse, ...] = Field(min_length=1)
    maximum_caption_characters: int = Field(gt=0)
    maximum_printed_number_characters: int = Field(gt=0)
    maximum_crop_bytes: int = Field(gt=0)

    @model_validator(mode="after")
    def validate_candidates(self) -> Self:
        candidate_ids = {
            candidate.candidate_id
            for candidate in self.candidates
        }
        ordinals = {
            candidate.candidate_ordinal
            for candidate in self.candidates
        }

        if len(candidate_ids) != len(self.candidates):
            raise ValueError("Figure candidate IDs must be unique")

        if len(ordinals) != len(self.candidates):
            raise ValueError("Figure candidate ordinals must be unique")

        return self


class FigureCropRequest(ApiModel):
    candidate_id: int = Field(gt=0)
    figure_ordinal: int = Field(gt=0)
    printed_figure_number: str | None = Field(
        default=None,
        max_length=100,
    )
    raw_caption_text: str | None = Field(
        default=None,
        max_length=2_000,
    )


class FigureCropResponse(ApiModel):
    figure_id: int = Field(gt=0)
    document_page_id: int = Field(gt=0)
    media_asset_id: int = Field(gt=0)
    figure_ordinal: int = Field(gt=0)
    existing: bool