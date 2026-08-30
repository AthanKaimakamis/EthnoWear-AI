import httpx

from ethnowear_worker_common.api.errors import (
    WorkerApiContractError,
    WorkerApiRequestError,
    WorkerInputError,
)
from ethnowear_worker_common.api.models import FailureRequest
from ethnowear_vision_worker.errors import VisionContractError


VISION_FAILURE_CODES = {
    "response_schema": "VISION_RESPONSE_SCHEMA_INVALID",
    "response_json": "VISION_RESPONSE_JSON_INVALID",
    "response_truncated": "VISION_RESPONSE_TRUNCATED",
    "response_size": "VISION_RESPONSE_SIZE_INVALID",
    "model_mismatch": "VISION_MODEL_MISMATCH",
    "model_metadata": "VISION_MODEL_METADATA_INVALID",
    "control_characters": "VISION_SUGGESTION_CONTENT_INVALID",
    "suggestion_expansion": "VISION_SUGGESTION_EXPANSION_INVALID",
    "token_overlap": "VISION_SUGGESTION_OVERLAP_INVALID",
    "issue_excerpt": "VISION_ISSUE_EXCERPT_UNGROUNDED",
    "number_grounding": "VISION_NUMBER_GROUNDING_INVALID",
}


def classify_failure(error: Exception) -> FailureRequest:
    if isinstance(error, VisionContractError):
        retryable = error.stage == "response_truncated"
        return FailureRequest(
            error_code=VISION_FAILURE_CODES.get(
                error.stage,
                "VISION_CONTRACT_INVALID",
            ),
            safe_error_message="The vision dependency returned an invalid result",
            retryable=retryable,
        )

    if isinstance(error, WorkerApiContractError):
        return FailureRequest(
            error_code="VISION_CONTRACT_INVALID",
            safe_error_message="The vision input or dependency response was invalid",
            retryable=False,
        )

    if isinstance(error, WorkerApiRequestError):
        retryable = (
                error.status_code == 429
                or error.status_code >= 500
        )

        return FailureRequest(
            error_code="WORKER_API_REQUEST_FAILED",
            safe_error_message="The worker API rejected the vision operation",
            retryable=retryable,
        )

    if isinstance(error, WorkerInputError):
        return FailureRequest(
            error_code="VISION_INPUT_INVALID",
            safe_error_message="The claimed vision input was invalid",
            retryable=False,
        )

    if isinstance(error, httpx.TimeoutException):
        return FailureRequest(
            error_code="VISION_MODEL_TIMEOUT",
            safe_error_message="The vision model request timed out",
            retryable=True,
        )

    if isinstance(error, httpx.TransportError):
        return FailureRequest(
            error_code="VISION_MODEL_UNAVAILABLE",
            safe_error_message="The vision model was temporarily unavailable",
            retryable=True,
        )

    if isinstance(error, TimeoutError):
        return FailureRequest(
            error_code="VISION_JOB_TIMEOUT",
            safe_error_message="The vision job exceeded its time limit",
            retryable=True,
        )

    if isinstance(error, ValueError):
        return FailureRequest(
            error_code="VISION_INPUT_INVALID",
            safe_error_message="The vision operation received invalid input",
            retryable=False,
        )

    if isinstance(error, RuntimeError):
        return FailureRequest(
            error_code="VISION_MODEL_UNAVAILABLE",
            safe_error_message="The configured vision model was unavailable",
            retryable=True,
        )

    return FailureRequest(
        error_code="VISION_INTERNAL_ERROR",
        safe_error_message="The vision assessment failed safely",
        retryable=False,
    )
