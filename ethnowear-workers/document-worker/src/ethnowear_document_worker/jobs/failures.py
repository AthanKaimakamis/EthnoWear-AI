import httpx

from ethnowear_worker_common.api.errors import (
    WorkerApiContractError,
    WorkerInputError
)
from ethnowear_worker_common.api.models import FailureRequest
from ethnowear_document_worker.pdf.inspector import PdfInspectionError
from ethnowear_document_worker.pdf.renderer import PdfRenderError
from ethnowear_document_worker.jobs.ocr import OcrTextLimitError
from ethnowear_document_worker.ocr.image import OcrImageError
from ethnowear_document_worker.ocr.process import (
    OcrProcessError,
    OcrProcessOutputLimitError,
    OcrProcessTimeoutError,
    OcrProcessUnavailableError,
)
from ethnowear_document_worker.ocr.serialization import OcrOutputLimitError
from ethnowear_document_worker.ocr.tsv import InvalidTsvError
from ethnowear_document_worker.layout.detector import LayoutDetectionError
from ethnowear_document_worker.layout.preprocessing import ImagePreprocessingError
from ethnowear_document_worker.ocr.pipeline import OcrPipelineError
from ethnowear_document_worker.jobs.page_extraction import PageExtractionTargetContractError


def classify_failure(error: Exception) -> FailureRequest:
    if isinstance(error, PageExtractionTargetContractError):
        return FailureRequest(
            error_code="PAGE_EXTRACTION_TARGET_INVALID",
            safe_error_message=(
                "The requested page rendition was absent from the extraction manifest"
            ),
            retryable=False,
        )
    if isinstance(error, (LayoutDetectionError, ImagePreprocessingError, OcrPipelineError)):
        return FailureRequest(
            error_code="OCR_LAYOUT_INVALID",
            safe_error_message="The OCR page layout could not be processed safely",
            retryable=False,
        )
    if isinstance(error, OcrImageError):
        return FailureRequest(
            error_code="OCR_INPUT_INVALID",
            safe_error_message="The OCR page image could not be accepted",
            retryable=False,
        )

    if isinstance(
            error,
            (
                    OcrTextLimitError,
                    OcrOutputLimitError,
                    OcrProcessOutputLimitError,
            ),
    ):
        return FailureRequest(
            error_code="OCR_OUTPUT_TOO_LARGE",
            safe_error_message="The OCR result exceeded the configured limits",
            retryable=False,
        )

    if isinstance(error, OcrProcessTimeoutError):
        return FailureRequest(
            error_code="OCR_TIMEOUT",
            safe_error_message="OCR processing exceeded its time limit",
            retryable=True,
        )

    if isinstance(error, OcrProcessUnavailableError):
        return FailureRequest(
            error_code="OCR_ENGINE_UNAVAILABLE",
            safe_error_message="The OCR engine was temporarily unavailable",
            retryable=True,
        )

    if isinstance(error, InvalidTsvError):
        return FailureRequest(
            error_code="OCR_OUTPUT_INVALID",
            safe_error_message="The OCR engine returned invalid structured output",
            retryable=False,
        )

    if isinstance(error, OcrProcessError):
        return FailureRequest(
            error_code="OCR_ENGINE_FAILED",
            safe_error_message="The OCR engine could not process the page",
            retryable=True,
        )

    if isinstance(error, WorkerInputError):
        return FailureRequest(
            error_code="PDF_INPUT_INVALID",
            safe_error_message="The PDF input could not be accepted",
            retryable=False,
        )

    if isinstance(error, PdfInspectionError):
        return FailureRequest(
            error_code="PDF_INSPECTION_FAILED",
            safe_error_message="The PDF could not be inspected safely",
            retryable=False,
        )

    if isinstance(error, PdfRenderError):
        return FailureRequest(
            error_code="PDF_RENDER_FAILED",
            safe_error_message="A PDF page could not be rendered safely",
            retryable=False,
        )

    if isinstance(error, (httpx.TimeoutException, httpx.TransportError)):
        return FailureRequest(
            error_code="WORKER_API_UNAVAILABLE",
            safe_error_message="A temporary worker API communication error occurred",
            retryable=True,
        )

    if isinstance(error, TimeoutError):
        return FailureRequest(
            error_code="JOB_TIMEOUT",
            safe_error_message="The document-processing job exceeded its time limit",
            retryable=True,
        )

    if isinstance(error, OSError):
        return FailureRequest(
            error_code="TEMPORARY_FILE_ERROR",
            safe_error_message="A temporary file operation failed",
            retryable=True,
        )

    if isinstance(error, WorkerApiContractError):
        return FailureRequest(
            error_code="WORKER_API_CONTRACT_INVALID",
            safe_error_message="The worker API returned an invalid response",
            retryable=False,
        )

    return FailureRequest(
        error_code="WORKER_INTERNAL_ERROR",
        safe_error_message="The document-processing job failed safely",
        retryable=False,
    )
