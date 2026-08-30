from ethnowear_indexer.api.client import IndexerApiClient
from ethnowear_indexer.api.models import ClaimResponse
from ethnowear_indexer.jobs.failures import classify_failure
from ethnowear_worker_common.jobs.execution import (
    JobOperation,
    execute_claimed_job as execute_common_claimed_job,
)


async def execute_claimed_job(
    *,
    api_client: IndexerApiClient,
    claim: ClaimResponse,
    operation: JobOperation,
) -> None:
    await execute_common_claimed_job(
        api_client=api_client,
        claim=claim,
        operation=operation,
        classify_failure=classify_failure,
    )
