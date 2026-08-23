import httpx

from ethnowear_worker.api.errors import (
    WorkerApiContractError,
    WorkerInputError
)
from ethnowear_worker.api.models import FailureRequest
from ethnowear_worker.pdf.inspector import PdfInspectionError
from ethnowear_worker.pdf.renderer import PdfRenderError


def classify_failure(error: Exception) -> FailureRequest:
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
