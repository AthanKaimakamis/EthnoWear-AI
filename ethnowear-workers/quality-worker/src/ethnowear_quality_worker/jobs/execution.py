from ethnowear_worker_common.api.errors import WorkerApiContractError
from ethnowear_worker_common.jobs.execution import (
    JobOperation,
    execute_claimed_job as execute_common_claimed_job,
)
from ethnowear_quality_worker.api.client import QualityWorkerApiClient
from ethnowear_quality_worker.api.models import (
    ClaimResponse,
    CompletionResponse,
    WorkerJobType,
)
from ethnowear_quality_worker.jobs.failures import classify_failure


async def execute_claimed_job(
        *,
        api_client: QualityWorkerApiClient,
        claim: ClaimResponse,
        operation: JobOperation,
) -> None:
    await execute_common_claimed_job(
        api_client=api_client,
        claim=claim,
        operation=operation,
        classify_failure=classify_failure,
        validate_completion=_validate_completion,
    )


def _validate_completion(
        completion: CompletionResponse,
        claim: ClaimResponse,
) -> None:
    if completion.job_id != claim.job_id:
        raise WorkerApiContractError("Completion job identity does not match claim")

    if completion.job_type is not WorkerJobType.OCR_QUALITY_ASSESSMENT:
        raise WorkerApiContractError(
            "Completion job type is not OCR_QUALITY_ASSESSMENT"
        )

    if completion.document_id != claim.target.document_id:
        raise WorkerApiContractError("Completion document identity does not match claim")

    if completion.document_page_id != claim.target.document_page_id:
        raise WorkerApiContractError("Completion page identity does not match claim")
