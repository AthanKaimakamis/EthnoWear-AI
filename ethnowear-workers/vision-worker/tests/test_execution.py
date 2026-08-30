from types import SimpleNamespace

import pytest

from ethnowear_worker_common.api.errors import WorkerApiContractError
from ethnowear_vision_worker.api.models import WorkerJobType
from ethnowear_vision_worker.jobs import execution
from ethnowear_vision_worker.jobs.failures import classify_failure


def claim() -> SimpleNamespace:
    return SimpleNamespace(
        job_id=20,
        job_type=WorkerJobType.VISION_OCR_ASSESSMENT,
        target=SimpleNamespace(document_id=10, document_page_id=98),
    )


def completion() -> SimpleNamespace:
    return SimpleNamespace(
        job_id=20,
        job_type=WorkerJobType.VISION_OCR_ASSESSMENT,
        document_id=10,
        document_page_id=98,
    )


def test_accepts_matching_vision_completion() -> None:
    execution._validate_completion(completion(), claim())


@pytest.mark.parametrize(
    ("field", "value", "message"),
    [
        ("job_id", 21, "job identity"),
        ("job_type", WorkerJobType.OCR, "job type"),
        ("document_id", 11, "document identity"),
        ("document_page_id", 99, "page identity"),
    ],
)
def test_rejects_mismatched_completion(
    field: str,
    value: object,
    message: str,
) -> None:
    response = completion()
    setattr(response, field, value)

    with pytest.raises(WorkerApiContractError, match=message):
        execution._validate_completion(response, claim())


@pytest.mark.asyncio
async def test_delegates_to_common_execution_with_vision_hooks(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    captured: dict[str, object] = {}

    async def fake_common_execution(**arguments: object) -> None:
        captured.update(arguments)

    async def operation(credentials: object) -> None:
        raise AssertionError("The common executor owns operation invocation")

    monkeypatch.setattr(
        execution,
        "execute_common_claimed_job",
        fake_common_execution,
    )

    api_client = object()
    claimed_job = claim()

    await execution.execute_claimed_job(
        api_client=api_client,
        claim=claimed_job,
        operation=operation,
    )

    assert captured == {
        "api_client": api_client,
        "claim": claimed_job,
        "operation": operation,
        "classify_failure": classify_failure,
        "validate_completion": execution._validate_completion,
    }
