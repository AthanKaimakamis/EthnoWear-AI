import httpx
import pytest

from ethnowear_worker_common.api.errors import (
    WorkerApiContractError,
    WorkerApiRequestError,
    WorkerInputError,
)

from ethnowear_figure_worker.crops.cropper import FigureCropError
from ethnowear_figure_worker.inspection.image import FigureImageError
from ethnowear_figure_worker.inspection.reevaluator import (
    CandidateReevaluationError,
)
from ethnowear_figure_worker.jobs.failures import classify_failure
from ethnowear_figure_worker.layout.parser import OcrLayoutParseError


@pytest.mark.parametrize(
    ("error", "code"),
    [
        (
            OcrLayoutParseError("secret OCR layout"),
            "FIGURE_LAYOUT_INVALID",
        ),
        (
            CandidateReevaluationError("secret candidate data"),
            "FIGURE_CANDIDATES_INVALID",
        ),
        (
            FigureCropError("/private/crop/path"),
            "FIGURE_CROP_INVALID",
        ),
        (
            FigureImageError("/private/image/path"),
            "FIGURE_INPUT_INVALID",
        ),
        (
            WorkerInputError("claim token"),
            "FIGURE_INPUT_INVALID",
        ),
        (
            WorkerApiContractError("raw backend body"),
            "WORKER_API_CONTRACT_INVALID",
        ),
        (
            RuntimeError("stack trace"),
            "FIGURE_INTERNAL_ERROR",
        ),
    ],
)
def test_non_retryable_failures_are_sanitized(
    error: Exception,
    code: str,
) -> None:
    failure = classify_failure(error)

    assert failure.error_code == code
    assert failure.retryable is False
    assert str(error) not in failure.safe_error_message


@pytest.mark.parametrize(
    ("status_code", "retryable"),
    [
        (400, False),
        (409, False),
        (429, True),
        (500, True),
        (503, True),
    ],
)
def test_api_request_retry_depends_on_status(
    status_code: int,
    retryable: bool,
) -> None:
    failure = classify_failure(WorkerApiRequestError(
        status_code,
        "SAFE_CODE",
        "safe backend message",
    ))

    assert failure.error_code == "WORKER_API_REQUEST_FAILED"
    assert failure.retryable is retryable


@pytest.mark.parametrize(
    "error",
    [
        httpx.ConnectError("backend unavailable"),
        httpx.ReadTimeout("backend timed out"),
        OSError("temporary filesystem failure"),
    ],
)
def test_temporary_dependency_failures_are_retryable(
    error: Exception,
) -> None:
    failure = classify_failure(error)

    assert failure.error_code == "FIGURE_DEPENDENCY_UNAVAILABLE"
    assert failure.retryable is True
    assert str(error) not in failure.safe_error_message
