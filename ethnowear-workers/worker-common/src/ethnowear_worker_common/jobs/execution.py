import asyncio
import logging
import time
from collections.abc import Awaitable, Callable
from typing import Protocol

from ethnowear_worker_common.api.errors import WorkerApiContractError
from ethnowear_worker_common.api.models import (
    CancellationResponse,
    ClaimResponse,
    CompletionResponse,
    FailureRequest,
    FailureResponse,
    HeartbeatResponse,
)
from ethnowear_worker_common.jobs.context import ClaimCredentials
from ethnowear_worker_common.jobs.heartbeat import HeartbeatSupervisor


class ClaimedJobClient(Protocol):
    async def heartbeat(
        self,
        credentials: ClaimCredentials,
        lease_seconds: int | None = None,
    ) -> HeartbeatResponse:
        ...

    async def complete(
        self,
        credentials: ClaimCredentials,
    ) -> CompletionResponse:
        ...

    async def fail(
        self,
        credentials: ClaimCredentials,
        failure: FailureRequest,
    ) -> FailureResponse:
        ...

    async def acknowledge_cancellation(
        self,
        credentials: ClaimCredentials,
    ) -> CancellationResponse:
        ...


JobOperation = Callable[[ClaimCredentials], Awaitable[None]]
FailureClassifier = Callable[[Exception], FailureRequest]
CompletionValidator = Callable[[CompletionResponse, ClaimResponse], None]


logger = logging.getLogger(__name__)


class JobCancellationRequested(RuntimeError):
    pass


async def execute_claimed_job(
    *,
    api_client: ClaimedJobClient,
    claim: ClaimResponse,
    operation: JobOperation,
    classify_failure: FailureClassifier,
    validate_completion: CompletionValidator | None = None,
) -> None:
    started_at = time.monotonic()
    credentials = ClaimCredentials.from_claim(claim)
    stop_heartbeat = asyncio.Event()
    cancellation_requested = asyncio.Event()

    heartbeat = HeartbeatSupervisor(
        api_client=api_client,
        credentials=credentials,
        interval_seconds=claim.limits.heartbeat_interval_seconds,
    )
    heartbeat_task = asyncio.create_task(
        heartbeat.run(stop_heartbeat, cancellation_requested)
    )

    async def work() -> None:
        await operation(credentials)
        _raise_if_stopped(heartbeat_task, cancellation_requested)
        try:
            completion = await api_client.complete(credentials)
        except Exception as error:
            _tag_processing_stage(error, "completion_submission")
            raise
        try:
            validator = validate_completion or _validate_completion
            validator(completion, claim)
        except Exception as error:
            _tag_processing_stage(error, "completion_validation")
            raise

    work_task = asyncio.create_task(work())
    cancellation_task = asyncio.create_task(cancellation_requested.wait())

    logger.info(
        "job_started",
        extra={
            "job_id": claim.job_id,
            "job_type": claim.job_type.value,
            "attempt": claim.attempt,
        },
    )

    try:
        try:
            async with asyncio.timeout(claim.limits.job_timeout_seconds):
                done, _ = await asyncio.wait(
                    {work_task, heartbeat_task, cancellation_task},
                    return_when=asyncio.FIRST_COMPLETED,
                )

                if cancellation_task in done and cancellation_requested.is_set():
                    raise JobCancellationRequested(
                        "Job cancellation was requested"
                    )

                if heartbeat_task in done:
                    _raise_if_heartbeat_failed(heartbeat_task)

                await work_task

            logger.info(
                "job_completed",
                extra=_log_context(claim, started_at),
            )

        except JobCancellationRequested:
            await _cancel_task(work_task)
            await api_client.acknowledge_cancellation(credentials)
            logger.info(
                "job_cancelled",
                extra=_log_context(claim, started_at),
            )

        except Exception as error:
            await _cancel_task(work_task)
            failure = classify_failure(error)
            await api_client.fail(credentials, failure)
            logger.error(
                "job_failed",
                extra={
                    **_log_context(claim, started_at),
                    "error_code": failure.error_code,
                    "retryable": failure.retryable,
                    "processing_stage": getattr(error, "processing_stage", None),
                    "validation_stage": getattr(error, "stage", None),
                    "exception_type": type(error).__name__,
                },
            )

    except asyncio.CancelledError:
        await _cancel_task(work_task)
        raise

    finally:
        stop_heartbeat.set()
        await _cancel_task(cancellation_task)
        await asyncio.gather(heartbeat_task, return_exceptions=True)


def _validate_completion(
    completion: CompletionResponse,
    claim: ClaimResponse,
) -> None:
    if completion.job_id != claim.job_id:
        raise WorkerApiContractError(
            "Completion job identity does not match claim"
        )

    if completion.job_type is not claim.job_type:
        raise WorkerApiContractError(
            "Completion job type does not match claim"
        )


def _raise_if_stopped(
    heartbeat_task: asyncio.Task[None],
    cancellation_requested: asyncio.Event,
) -> None:
    if cancellation_requested.is_set():
        raise JobCancellationRequested("Job cancellation was requested")

    _raise_if_heartbeat_failed(heartbeat_task)


def _raise_if_heartbeat_failed(
    heartbeat_task: asyncio.Task[None],
) -> None:
    if not heartbeat_task.done():
        return

    if heartbeat_task.cancelled():
        raise RuntimeError("Heartbeat task stopped unexpectedly")

    error = heartbeat_task.exception()

    if error is not None:
        raise error

    raise RuntimeError("Heartbeat task stopped unexpectedly")


async def _cancel_task(task: asyncio.Task[object]) -> None:
    if not task.done():
        task.cancel()

    await asyncio.gather(task, return_exceptions=True)


def _tag_processing_stage(error: Exception, stage: str) -> None:
    try:
        setattr(error, "processing_stage", stage)
    except (AttributeError, TypeError):
        return


def _log_context(
    claim: ClaimResponse,
    started_at: float,
) -> dict[str, object]:
    return {
        "job_id": claim.job_id,
        "job_type": claim.job_type.value,
        "attempt": claim.attempt,
        "duration_ms": int((time.monotonic() - started_at) * 1000),
    }
