from enum import StrEnum
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
        WorkerJobType.PAGE_EXTRACTION,
        WorkerJobType.OCR,
    )


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


class FigureCandidateRequest(ApiModel):
    candidate_ordinal: int = Field(gt=0)
    normalized_x: float = Field(ge=0.0, le=1.0)
    normalized_y: float = Field(ge=0.0, le=1.0)
    normalized_width: float = Field(gt=0.0, le=1.0)
    normalized_height: float = Field(gt=0.0, le=1.0)
    raw_caption_text: str | None = Field(default=None, max_length=2_000)
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


class OcrResultRequest(ApiModel):
    raw_text: str
    ocr_engine: str = Field(min_length=1, max_length=100)
    ocr_engine_version: str | None = Field(default=None, max_length=100)
    ocr_language: str | None = Field(default=None, max_length=20)
    ocr_confidence: float | None = Field(default=None, ge=0.0, le=1.0)
    parameters_json: str | None = None
    structured_output_json: str | None = None
    figure_candidates: tuple[FigureCandidateRequest, ...] = ()


class OcrResultResponse(ApiModel):
    ocr_result_id: int = Field(gt=0)
    job_id: int = Field(gt=0)
    document_id: int = Field(gt=0)
    page_id: int = Field(gt=0)
    input_media_id: int = Field(gt=0)
    existing: bool
