import httpx
import pytest

from ethnowear_worker_common.api.errors import (
    WorkerApiContractError,
    WorkerInputError,
)
from ethnowear_document_worker.jobs.failures import classify_failure
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
from ethnowear_document_worker.pdf.inspector import PdfInspectionError
from ethnowear_document_worker.pdf.renderer import PdfRenderError


@pytest.mark.parametrize(
    ("error", "code", "retryable"),
    [
        (OcrImageError("unsafe"), "OCR_INPUT_INVALID", False),
        (OcrTextLimitError("unsafe"), "OCR_OUTPUT_TOO_LARGE", False),
        (OcrOutputLimitError("unsafe"), "OCR_OUTPUT_TOO_LARGE", False),
        (
            OcrProcessOutputLimitError("unsafe"),
            "OCR_OUTPUT_TOO_LARGE",
            False,
        ),
        (OcrProcessTimeoutError("unsafe"), "OCR_TIMEOUT", True),
        (
            OcrProcessUnavailableError("unsafe"),
            "OCR_ENGINE_UNAVAILABLE",
            True,
        ),
        (InvalidTsvError("unsafe"), "OCR_OUTPUT_INVALID", False),
        (OcrProcessError("unsafe"), "OCR_ENGINE_FAILED", True),
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


@pytest.mark.parametrize(
    "error",
    [
        OcrImageError("token-and-/private/ocr-input.png"),
        OcrProcessError("token-and-/private/ocr-input.png"),
        InvalidTsvError("token-and-/private/ocr-input.png"),
    ],
)
def test_classify_ocr_failure_does_not_expose_original_message(
    error: Exception,
) -> None:
    serialized = classify_failure(error).model_dump_json()

    assert "token-and-" not in serialized
    assert "/private/ocr-input.png" not in serialized
