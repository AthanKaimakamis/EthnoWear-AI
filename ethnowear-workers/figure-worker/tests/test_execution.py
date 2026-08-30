from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest

from ethnowear_worker_common.api.errors import WorkerApiContractError
from ethnowear_worker_common.api.models import WorkerJobType

from ethnowear_figure_worker.jobs import execution
from ethnowear_figure_worker.jobs.failures import classify_failure


def claim() -> SimpleNamespace:
    return SimpleNamespace(
        job_id=41,
        target=SimpleNamespace(
            document_id=30,
            document_page_id=98,
        ),
    )


def completion() -> SimpleNamespace:
    return SimpleNamespace(
        job_id=41,
        job_type=WorkerJobType.EXTRACT_PAGE_FIGURES,
        document_id=30,
        document_page_id=98,
    )


def test_accepts_matching_completion() -> None:
    execution._validate_completion(  # noqa: SLF001
        completion(),
        claim(),
    )


@pytest.mark.parametrize(
    ("attribute", "value", "message"),
    [
        ("job_id", 42, "job identity"),
        ("job_type", WorkerJobType.OCR, "job type"),
        ("document_id", 31, "document identity"),
        ("document_page_id", 99, "page identity"),
    ],
)
def test_rejects_mismatched_completion(
    attribute: str,
    value: object,
    message: str,
) -> None:
    result = completion()
    setattr(result, attribute, value)

    with pytest.raises(WorkerApiContractError, match=message):
        execution._validate_completion(  # noqa: SLF001
            result,
            claim(),
        )


@pytest.mark.asyncio
async def test_delegates_to_shared_execution_contract(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    shared_execution = AsyncMock()
    monkeypatch.setattr(
        execution,
        "execute_common_claimed_job",
        shared_execution,
    )
    api_client = object()
    job_claim = claim()

    async def operation(_credentials: object) -> None:
        return None

    await execution.execute_claimed_job(
        api_client=api_client,  # type: ignore[arg-type]
        claim=job_claim,  # type: ignore[arg-type]
        operation=operation,
    )

    shared_execution.assert_awaited_once_with(
        api_client=api_client,
        claim=job_claim,
        operation=operation,
        classify_failure=classify_failure,
        validate_completion=execution._validate_completion,  # noqa: SLF001
    )
