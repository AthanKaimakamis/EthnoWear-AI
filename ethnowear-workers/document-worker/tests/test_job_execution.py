import asyncio

import httpx
import pytest

from ethnowear_document_worker.api.models import CompletionResponse, WorkerJobType
from ethnowear_document_worker.jobs.execution import execute_claimed_job
from test_ocr_job import claim as make_claim


class FakeApiClient:
    def __init__(
        self,
        fail_reporting: bool = False,
        completion: CompletionResponse | None = None,
    ) -> None:
        self.calls: list[str] = []
        self.failure = None
        self.fail_reporting = fail_reporting
        self.completion = completion or CompletionResponse(
            job_id=12,
            job_type=WorkerJobType.OCR,
            document_id=7,
            document_page_id=21,
            page_count=0,
            queued_ocr_jobs=0,
            queued_quality_assessment_jobs=1,
            queued_vision_assessment_jobs=0,
            existing=False,
        )

    async def complete(self, credentials) -> CompletionResponse:
        self.calls.append("complete")
        return self.completion

    async def fail(self, credentials, failure) -> None:
        self.calls.append("fail")
        self.failure = failure
        if self.fail_reporting:
            raise httpx.ConnectError("backend unavailable")

    async def acknowledge_cancellation(self, credentials) -> None:
        self.calls.append("cancelled")


class QuietHeartbeat:
    def __init__(self, **kwargs) -> None:
        pass

    async def run(self, stop_event, cancellation_event) -> None:
        await stop_event.wait()


class CancellingHeartbeat:
    def __init__(self, **kwargs) -> None:
        pass

    async def run(self, stop_event, cancellation_event) -> None:
        cancellation_event.set()


class FailingHeartbeat:
    def __init__(self, **kwargs) -> None:
        pass

    async def run(self, stop_event, cancellation_event) -> None:
        raise httpx.ConnectError("heartbeat unavailable")


def use_heartbeat(monkeypatch: pytest.MonkeyPatch, heartbeat_type) -> None:
    monkeypatch.setattr(
        "ethnowear_worker_common.jobs.execution.HeartbeatSupervisor",
        heartbeat_type,
    )


def test_execute_claimed_job_completes_successful_operation(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    use_heartbeat(monkeypatch, QuietHeartbeat)
    api = FakeApiClient()
    calls: list[str] = []

    async def operation(credentials) -> None:
        calls.append("operation")

    asyncio.run(execute_claimed_job(
        api_client=api,
        claim=make_claim(),
        operation=operation,
    ))

    assert calls == ["operation"]
    assert api.calls == ["complete"]


def test_execute_claimed_job_cancels_work_and_acknowledges_cancellation(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    use_heartbeat(monkeypatch, CancellingHeartbeat)
    api = FakeApiClient()
    operation_cancelled = False

    async def operation(credentials) -> None:
        nonlocal operation_cancelled
        try:
            await asyncio.Event().wait()
        finally:
            operation_cancelled = True

    asyncio.run(execute_claimed_job(
        api_client=api,
        claim=make_claim(),
        operation=operation,
    ))

    assert operation_cancelled is True
    assert api.calls == ["cancelled"]


def test_execute_claimed_job_reports_sanitized_operation_failure(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    use_heartbeat(monkeypatch, QuietHeartbeat)
    api = FakeApiClient()

    async def operation(credentials) -> None:
        raise ValueError("/private/tmp/secret-input.png")

    asyncio.run(execute_claimed_job(
        api_client=api,
        claim=make_claim(),
        operation=operation,
    ))

    assert api.calls == ["fail"]
    assert api.failure.error_code == "WORKER_INTERNAL_ERROR"
    assert "/private/tmp" not in api.failure.safe_error_message


def test_execute_claimed_job_reports_heartbeat_failure_and_cancels_work(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    use_heartbeat(monkeypatch, FailingHeartbeat)
    api = FakeApiClient()
    operation_cancelled = False

    async def operation(credentials) -> None:
        nonlocal operation_cancelled
        try:
            await asyncio.Event().wait()
        finally:
            operation_cancelled = True

    asyncio.run(execute_claimed_job(
        api_client=api,
        claim=make_claim(),
        operation=operation,
    ))

    assert operation_cancelled is True
    assert api.failure.error_code == "WORKER_API_UNAVAILABLE"


def test_execute_claimed_job_enforces_timeout(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    use_heartbeat(monkeypatch, QuietHeartbeat)
    api = FakeApiClient()
    job = make_claim()
    job.limits.job_timeout_seconds = 0.01

    async def operation(credentials) -> None:
        await asyncio.Event().wait()

    asyncio.run(execute_claimed_job(
        api_client=api,
        claim=job,
        operation=operation,
    ))

    assert api.failure.error_code == "JOB_TIMEOUT"
    assert api.failure.retryable is True


def test_execute_claimed_job_propagates_worker_shutdown(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    use_heartbeat(monkeypatch, QuietHeartbeat)
    api = FakeApiClient()
    operation_cancelled = False

    async def operation(credentials) -> None:
        nonlocal operation_cancelled
        try:
            await asyncio.Event().wait()
        finally:
            operation_cancelled = True

    async def execute() -> None:
        task = asyncio.create_task(execute_claimed_job(
            api_client=api,
            claim=make_claim(),
            operation=operation,
        ))
        await asyncio.sleep(0)
        task.cancel()
        with pytest.raises(asyncio.CancelledError):
            await task

    asyncio.run(execute())

    assert operation_cancelled is True
    assert api.calls == []


def test_execute_claimed_job_propagates_failure_reporting_outage(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    use_heartbeat(monkeypatch, QuietHeartbeat)
    api = FakeApiClient(fail_reporting=True)

    async def operation(credentials) -> None:
        raise ValueError("processing failed")

    with pytest.raises(httpx.ConnectError):
        asyncio.run(execute_claimed_job(
            api_client=api,
            claim=make_claim(),
            operation=operation,
        ))


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("job_id", 99),
        ("job_type", WorkerJobType.PAGE_EXTRACTION),
        ("document_id", 99),
        ("document_page_id", 99),
    ],
)
def test_execute_claimed_job_reports_completion_identity_mismatch(
    monkeypatch: pytest.MonkeyPatch,
    field: str,
    value,
) -> None:
    use_heartbeat(monkeypatch, QuietHeartbeat)
    completion_values = {
        "job_id": 12,
        "job_type": WorkerJobType.OCR,
        "document_id": 7,
        "document_page_id": 21,
        "page_count": 0,
        "queued_ocr_jobs": 0,
        "queued_quality_assessment_jobs": 1,
        "queued_vision_assessment_jobs": 0,
        "existing": False,
    }
    completion_values[field] = value
    api = FakeApiClient(
        completion=CompletionResponse(**completion_values)
    )

    async def operation(credentials) -> None:
        pass

    asyncio.run(execute_claimed_job(
        api_client=api,
        claim=make_claim(),
        operation=operation,
    ))

    assert api.calls == ["complete", "fail"]
    assert api.failure.error_code == "WORKER_API_CONTRACT_INVALID"
