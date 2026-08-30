import httpx
import pytest

from ethnowear_worker_common.api.errors import (
    WorkerApiContractError,
    WorkerApiRequestError,
    WorkerInputError,
)
from ethnowear_vision_worker.errors import VisionContractError
from ethnowear_vision_worker.jobs.failures import classify_failure


@pytest.mark.parametrize(
    ("error", "code", "retryable"),
    [
        (
            WorkerApiContractError("private malformed response"),
            "VISION_CONTRACT_INVALID",
            False,
        ),
        (
            VisionContractError("response_schema", "private response"),
            "VISION_RESPONSE_SCHEMA_INVALID",
            False,
        ),
        (
            VisionContractError("response_truncated", "private response"),
            "VISION_RESPONSE_TRUNCATED",
            True,
        ),
        (
            VisionContractError("number_grounding", "private OCR numbers"),
            "VISION_NUMBER_GROUNDING_INVALID",
            False,
        ),
        (
            WorkerInputError("/private/media/page.png"),
            "VISION_INPUT_INVALID",
            False,
        ),
        (
            httpx.ReadTimeout("private timeout details"),
            "VISION_MODEL_TIMEOUT",
            True,
        ),
        (
            httpx.ConnectError("http://private-ollama:11434"),
            "VISION_MODEL_UNAVAILABLE",
            True,
        ),
        (
            TimeoutError("private job details"),
            "VISION_JOB_TIMEOUT",
            True,
        ),
        (
            ValueError("/tmp/private-input.png"),
            "VISION_INPUT_INVALID",
            False,
        ),
        (
            RuntimeError("private model response"),
            "VISION_MODEL_UNAVAILABLE",
            True,
        ),
        (
            Exception("secret stack trace"),
            "VISION_INTERNAL_ERROR",
            False,
        ),
    ],
)
def test_classifies_failures_without_exposing_error_text(
    error: Exception,
    code: str,
    retryable: bool,
) -> None:
    failure = classify_failure(error)

    assert failure.error_code == code
    assert failure.retryable is retryable
    assert str(error) not in failure.safe_error_message


@pytest.mark.parametrize(
    ("status_code", "retryable"),
    [
        (400, False),
        (409, False),
        (410, False),
        (429, True),
        (500, True),
        (503, True),
    ],
)
def test_worker_api_retryability_follows_status(
    status_code: int,
    retryable: bool,
) -> None:
    error = WorkerApiRequestError(
        status_code=status_code,
        code="PRIVATE_API_CODE",
        safe_message="private backend message",
    )

    failure = classify_failure(error)

    assert failure.error_code == "WORKER_API_REQUEST_FAILED"
    assert failure.retryable is retryable
    assert "PRIVATE_API_CODE" not in failure.safe_error_message
    assert "private backend message" not in failure.safe_error_message
