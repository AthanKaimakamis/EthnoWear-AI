from ethnowear_document_worker.api.client import WorkerApiClient
from ethnowear_document_worker.jobs.failures import classify_failure
from ethnowear_worker_common.api.errors import WorkerApiContractError
from ethnowear_worker_common.api.models import (
    ClaimResponse,
    CompletionResponse,
    WorkerJobType,
)
from ethnowear_worker_common.jobs.execution import (
    JobOperation,
    execute_claimed_job as execute_common_claimed_job,
)


async def execute_claimed_job(
        *,
        api_client: WorkerApiClient,
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


def _validate_completion(completion: CompletionResponse, claim: ClaimResponse) -> None:
    if completion.job_id != claim.job_id:
        raise WorkerApiContractError("Completion job identity does not match claim")

    if completion.job_type is not claim.job_type:
        raise WorkerApiContractError("Completion job type does not match claim")

    if completion.document_id != claim.target.document_id:
        raise WorkerApiContractError("Completion document identity does not match claim")

    if (claim.job_type is WorkerJobType.OCR
            and completion.document_page_id
            != claim.target.document_page_id):
        raise WorkerApiContractError("Completion page identity does not match claim")
