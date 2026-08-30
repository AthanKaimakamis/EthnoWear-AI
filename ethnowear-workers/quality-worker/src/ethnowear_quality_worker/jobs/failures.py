import httpx

from ethnowear_worker_common.api.errors import (
    WorkerApiContractError,
    WorkerApiRequestError,
    WorkerInputError,
)
from ethnowear_worker_common.api.models import FailureRequest

from ethnowear_quality_worker.input.image import OcrImageError
from ethnowear_quality_worker.quality.evaluator import QualityAssessmentError


def classify_failure(error: Exception) -> FailureRequest:
    if isinstance(error, QualityAssessmentError):
        return FailureRequest(
            error_code="QUALITY_ASSESSMENT_INVALID",
            safe_error_message=(
                "The OCR quality assessment could not be produced safely"
            ),
            retryable=False,
        )

    if isinstance(error, (OcrImageError, WorkerInputError)):
        return FailureRequest(
            error_code="QUALITY_INPUT_INVALID",
            safe_error_message="The OCR quality input could not be accepted",
            retryable=False,
        )

    if isinstance(error, WorkerApiContractError):
        return FailureRequest(
            error_code="WORKER_API_CONTRACT_INVALID",
            safe_error_message="The worker API returned an invalid quality contract",
            retryable=False,
        )

    if isinstance(error, WorkerApiRequestError):
        return FailureRequest(
            error_code="WORKER_API_REQUEST_FAILED",
            safe_error_message="The worker API rejected the quality operation",
            retryable=error.status_code == 429 or error.status_code >= 500,
        )

    if isinstance(error, (httpx.TimeoutException, httpx.TransportError, OSError)):
        return FailureRequest(
            error_code="QUALITY_DEPENDENCY_UNAVAILABLE",
            safe_error_message="A temporary quality worker dependency failed",
            retryable=True,
        )

    return FailureRequest(
        error_code="QUALITY_INTERNAL_ERROR",
        safe_error_message="The OCR quality assessment failed safely",
        retryable=False,
    )
