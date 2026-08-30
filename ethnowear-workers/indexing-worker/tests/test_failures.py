import httpx

from ethnowear_indexer.jobs.failures import classify_failure
from ethnowear_worker_common.api.errors import WorkerApiRequestError


def test_spring_embedding_unavailable_remains_retryable() -> None:
    failure = classify_failure(WorkerApiRequestError(
        status_code=503,
        code="EMBEDDING_UNAVAILABLE",
        safe_message="safe backend message",
    ))

    assert failure.error_code == "EMBEDDING_UNAVAILABLE"
    assert failure.retryable is True


def test_worker_api_transport_failure_is_retryable() -> None:
    failure = classify_failure(httpx.ConnectError("private network detail"))

    assert failure.error_code == "WORKER_API_UNAVAILABLE"
    assert failure.retryable is True
    assert "private network detail" not in failure.safe_error_message
