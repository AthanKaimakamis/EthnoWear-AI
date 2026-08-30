import httpx
from qdrant_client.http.exceptions import (
    ResponseHandlingException,
    UnexpectedResponse
)

from ethnowear_worker_common.api.errors import (
    WorkerApiContractError,
    WorkerApiRequestError
)
from ethnowear_worker_common.api.models import FailureRequest


def classify_failure(error: Exception) -> FailureRequest:
    if isinstance(error, WorkerApiContractError):
        return FailureRequest(
            error_code="INDEX_CONTRACT_INVALID",
            safe_error_message="The indexing input or dependency response was invalid",
            retryable=False,
        )

    if isinstance(error, WorkerApiRequestError):
        retryable = error.status_code == 429 or error.status_code >= 500

        if error.code == "EMBEDDING_UNAVAILABLE":
            return FailureRequest(
                error_code="EMBEDDING_UNAVAILABLE",
                safe_error_message="The embedding service was temporarily unavailable",
                retryable=True,
            )

        return FailureRequest(
            error_code="WORKER_API_REQUEST_FAILED",
            safe_error_message="The worker API rejected the indexing operation",
            retryable=retryable,
        )

    if isinstance(error, httpx.TimeoutException):
        return FailureRequest(
            error_code="EMBEDDING_TIMEOUT",
            safe_error_message="The worker API embedding request timed out",
            retryable=True,
        )

    if isinstance(error, httpx.TransportError):
        return FailureRequest(
            error_code="WORKER_API_UNAVAILABLE",
            safe_error_message="The worker API was temporarily unavailable",
            retryable=True,
        )

    if isinstance(error, ResponseHandlingException):
        return FailureRequest(
            error_code="VECTOR_STORE_UNAVAILABLE",
            safe_error_message="The vector store was temporarily unavailable",
            retryable=True,
        )

    if isinstance(error, UnexpectedResponse):
        retryable = (
                error.status_code == 429
                or error.status_code >= 500
        )

        return FailureRequest(
            error_code="VECTOR_STORE_REQUEST_FAILED",
            safe_error_message="The vector store rejected the indexing operation",
            retryable=retryable,
        )

    if isinstance(error, TimeoutError):
        return FailureRequest(
            error_code="INDEX_JOB_TIMEOUT",
            safe_error_message="The indexing job exceeded its time limit",
            retryable=True,
        )

    if isinstance(error, ValueError):
        return FailureRequest(
            error_code="INDEX_INPUT_INVALID",
            safe_error_message="The indexing input was invalid",
            retryable=False,
        )

    return FailureRequest(
        error_code="INDEX_INTERNAL_ERROR",
        safe_error_message="The indexing job failed safely",
        retryable=False,
    )
