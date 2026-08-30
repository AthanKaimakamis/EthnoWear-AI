import httpx

from ethnowear_worker_common.api.errors import (
    WorkerApiContractError,
    WorkerApiRequestError,
    WorkerInputError,
)
from ethnowear_worker_common.api.models import FailureRequest

from ethnowear_figure_worker.crops.cropper import FigureCropError
from ethnowear_figure_worker.inspection.image import FigureImageError
from ethnowear_figure_worker.inspection.reevaluator import (
    CandidateReevaluationError,
)
from ethnowear_figure_worker.layout.parser import OcrLayoutParseError


def classify_failure(error: Exception) -> FailureRequest:
    if isinstance(error, OcrLayoutParseError):
        return FailureRequest(
            error_code="FIGURE_LAYOUT_INVALID",
            safe_error_message="The OCR page layout could not be processed safely",
            retryable=False,
        )

    if isinstance(error, CandidateReevaluationError):
        return FailureRequest(
            error_code="FIGURE_CANDIDATES_INVALID",
            safe_error_message="The page figure candidates could not be evaluated safely",
            retryable=False,
        )

    if isinstance(error, FigureCropError):
        return FailureRequest(
            error_code="FIGURE_CROP_INVALID",
            safe_error_message="A page figure crop could not be produced safely",
            retryable=False,
        )

    if isinstance(error, (FigureImageError, WorkerInputError)):
        return FailureRequest(
            error_code="FIGURE_INPUT_INVALID",
            safe_error_message="The page image could not be accepted",
            retryable=False,
        )

    if isinstance(error, WorkerApiContractError):
        return FailureRequest(
            error_code="WORKER_API_CONTRACT_INVALID",
            safe_error_message="The worker API returned an invalid figure contract",
            retryable=False,
        )

    if isinstance(error, WorkerApiRequestError):
        return FailureRequest(
            error_code="WORKER_API_REQUEST_FAILED",
            safe_error_message="The worker API rejected the figure operation",
            retryable=(error.status_code == 429 or error.status_code >= 500),
        )

    if isinstance(
            error,
            (
                    httpx.TimeoutException,
                    httpx.TransportError,
                    OSError,
            ),
    ):
        return FailureRequest(
            error_code="FIGURE_DEPENDENCY_UNAVAILABLE",
            safe_error_message="A temporary figure worker dependency failed",
            retryable=True,
        )

    return FailureRequest(
        error_code="FIGURE_INTERNAL_ERROR",
        safe_error_message="Page figure extraction failed safely",
        retryable=False,
    )
