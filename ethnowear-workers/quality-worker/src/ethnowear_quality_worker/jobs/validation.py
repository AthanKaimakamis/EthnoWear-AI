from ethnowear_worker_common.api.errors import WorkerApiContractError
from ethnowear_worker_common.api.models import ClaimResponse

from ethnowear_quality_worker.api.models import (
    QualityAssessmentContextResponse,
    QualityAssessmentResponse,
)
from ethnowear_quality_worker.input.image import OcrImageInfo


def validate_quality_context(
    context: QualityAssessmentContextResponse,
    claim: ClaimResponse,
    image: OcrImageInfo,
) -> None:
    if context.job_id != claim.job_id:
        raise WorkerApiContractError(
            "Quality context job identity does not match claim"
        )
    if context.document_id != claim.target.document_id:
        raise WorkerApiContractError(
            "Quality context document identity does not match claim"
        )
    if context.page_id != claim.target.document_page_id:
        raise WorkerApiContractError(
            "Quality context page identity does not match claim"
        )
    if context.image_size_bytes > claim.limits.maximum_input_bytes:
        raise WorkerApiContractError(
            "Quality context image size exceeds the claim limit"
        )
    if context.image_width is not None and context.image_width != image.width:
        raise WorkerApiContractError(
            "Quality context image width does not match input"
        )
    if context.image_height is not None and context.image_height != image.height:
        raise WorkerApiContractError(
            "Quality context image height does not match input"
        )


def validate_quality_response(
    response: QualityAssessmentResponse,
    context: QualityAssessmentContextResponse,
    claim: ClaimResponse,
) -> None:
    if response.job_id != claim.job_id:
        raise WorkerApiContractError("Quality response job identity does not match claim")
    if response.document_id != claim.target.document_id:
        raise WorkerApiContractError(
            "Quality response document identity does not match claim"
        )
    if response.page_id != claim.target.document_page_id:
        raise WorkerApiContractError("Quality response page identity does not match claim")
    if response.ocr_result_id != context.ocr_result_id:
        raise WorkerApiContractError("Quality response OCR result does not match context")
    if response.input_media_id != context.input_media_id:
        raise WorkerApiContractError("Quality response input media does not match context")
