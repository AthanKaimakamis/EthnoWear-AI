from ethnowear_worker_common.api.errors import WorkerApiContractError
from ethnowear_document_worker.api.models import (
    FigureCandidateRequest,
    OcrResultRequest,
    OcrResultResponse,
)
from ethnowear_document_worker.ocr.models import OcrOutput
from ethnowear_document_worker.ocr.serialization import (
    build_parameters,
    build_structured_output,
)
from ethnowear_worker_common.api.models import ClaimResponse


class OcrTextLimitError(ValueError):
    pass


def build_ocr_result(
        output: OcrOutput,
        *,
        maximum_text_characters: int,
        maximum_output_bytes: int,
        oem: int,
        psm: int,
        preprocessed: bool
) -> OcrResultRequest:
    if maximum_text_characters <= 0:
        raise ValueError("Maximum OCR text length must be positive")

    if len(output.raw_text) > maximum_text_characters:
        raise OcrTextLimitError("OCR text exceeds the maximum character count")

    structured_output = build_structured_output(
        output,
        maximum_output_bytes,
    )

    parameters = build_parameters(
        language=output.language,
        oem=oem,
        psm=output.psm,
        preprocessed=preprocessed,
        strategy=output.strategy,
        preprocessing=output.preprocessing,
    )

    return OcrResultRequest(
        raw_text=output.raw_text,
        ocr_engine=output.engine_name,
        ocr_engine_version=output.engine_version,
        ocr_language=output.language,
        ocr_confidence=output.mean_confidence,
        parameters_json=parameters,
        structured_output_json=structured_output,
        figure_candidates=tuple(
            FigureCandidateRequest(
                candidate_ordinal=candidate.candidate_ordinal,
                normalized_x=candidate.normalized_x,
                normalized_y=candidate.normalized_y,
                normalized_width=candidate.normalized_width,
                normalized_height=candidate.normalized_height,
                raw_caption_text=candidate.raw_caption_text,
                detection_confidence=candidate.detection_confidence,
            )
            for candidate in output.figure_candidates
        ),
    )


def validate_ocr_result_response(
        response: OcrResultResponse,
        claim: ClaimResponse,
) -> None:
    if response.job_id != claim.job_id:
        raise WorkerApiContractError("OCR result job identity does not match claim")

    if response.document_id != claim.target.document_id:
        raise WorkerApiContractError("OCR result document identity does not match claim")

    if response.page_id != claim.target.document_page_id:
        raise WorkerApiContractError("OCR result page identity does not match claim")
