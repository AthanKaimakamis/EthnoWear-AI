import httpx
import pytest

from ethnowear_worker.api.errors import (
    WorkerApiContractError,
    WorkerInputError,
)
from ethnowear_worker.jobs.failures import classify_failure
from ethnowear_worker.pdf.inspector import PdfInspectionError
from ethnowear_worker.pdf.renderer import PdfRenderError


@pytest.mark.parametrize(
    ("error", "code", "retryable"),
    [
        (WorkerInputError("unsafe"), "PDF_INPUT_INVALID", False),
        (PdfInspectionError("unsafe"), "PDF_INSPECTION_FAILED", False),
        (PdfRenderError("unsafe"), "PDF_RENDER_FAILED", False),
        (httpx.ReadTimeout("unsafe"), "WORKER_API_UNAVAILABLE", True),
        (httpx.ConnectError("unsafe"), "WORKER_API_UNAVAILABLE", True),
        (TimeoutError("unsafe"), "JOB_TIMEOUT", True),
        (OSError("unsafe"), "TEMPORARY_FILE_ERROR", True),
        (
            WorkerApiContractError("unsafe"),
            "WORKER_API_CONTRACT_INVALID",
            False,
        ),
        (RuntimeError("unsafe"), "WORKER_INTERNAL_ERROR", False),
    ],
)
def test_classify_failure_maps_controlled_error(
    error: Exception,
    code: str,
    retryable: bool,
) -> None:
    failure = classify_failure(error)

    assert failure.error_code == code
    assert failure.retryable is retryable


def test_classify_failure_does_not_expose_original_message() -> None:
    secret = "token-and-/private/path-that-must-not-leak"

    failure = classify_failure(RuntimeError(secret))

    serialized = failure.model_dump_json()
    assert secret not in serialized
    assert "/private/path" not in serialized
