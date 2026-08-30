import httpx

from ethnowear_worker_common.api.errors import WorkerApiContractError

from ethnowear_quality_worker.input.image import OcrImageError
from ethnowear_quality_worker.jobs.failures import classify_failure
from ethnowear_quality_worker.quality.evaluator import QualityAssessmentError


def test_quality_evaluation_error_is_not_retryable() -> None:
    failure = classify_failure(QualityAssessmentError("unsafe internal detail"))

    assert failure.error_code == "QUALITY_ASSESSMENT_INVALID"
    assert failure.retryable is False
    assert "unsafe internal detail" not in failure.safe_error_message


def test_invalid_image_is_not_retryable() -> None:
    failure = classify_failure(OcrImageError("local path"))

    assert failure.error_code == "QUALITY_INPUT_INVALID"
    assert failure.retryable is False


def test_api_transport_failure_is_retryable() -> None:
    failure = classify_failure(httpx.ConnectError("backend unavailable"))

    assert failure.error_code == "QUALITY_DEPENDENCY_UNAVAILABLE"
    assert failure.retryable is True


def test_contract_failure_is_not_retryable() -> None:
    failure = classify_failure(WorkerApiContractError("invalid response"))

    assert failure.error_code == "WORKER_API_CONTRACT_INVALID"
    assert failure.retryable is False
