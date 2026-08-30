import httpx

from ethnowear_worker_common.api.client import WorkerApiTransport
from ethnowear_worker_common.api.errors import WorkerApiContractError
from ethnowear_worker_common.jobs.context import ClaimCredentials

from ethnowear_indexer.api.models import *
from ethnowear_indexer.config import IndexerSettings


class IndexerApiClient(WorkerApiTransport):
    CLAIM_PATH = f"{WorkerApiTransport.JOBS_PATH}/claim"

    def __init__(self, settings: IndexerSettings, http_client: httpx.AsyncClient) -> None:
        super().__init__(settings.api_base_url, settings.worker_id, settings.api_token, http_client)

        self._settings = settings

    async def claim(self) -> ClaimResponse | None:
        request = ClaimRequest(
            worker_id=self._settings.worker_id,
            lease_seconds=self._settings.lease_seconds,
        )
        response = await self._http_client.post(
            self._url(self.CLAIM_PATH),
            headers=self._service_headers(),
            json=request.model_dump(by_alias=True, mode="json"),
        )

        if response.status_code == 204:
            return None

        self._require_status(response, 200)
        return self._parse_response(response, ClaimResponse, "claim")

    async def heartbeat(
            self,
            credentials: ClaimCredentials,
            lease_seconds: int | None = None,
    ) -> HeartbeatResponse:
        request = HeartbeatRequest(lease_seconds=lease_seconds)
        response = await self._http_client.post(
            self._url(self._job_path(credentials.job_id, "heartbeat")),
            headers=self._claim_headers(credentials.claim_token),
            json=request.model_dump(by_alias=True, mode="json"),
        )
        self._require_status(response, 200)
        return self._parse_response(response, HeartbeatResponse, "heartbeat")

    async def get_indexing_context(self, credentials: ClaimCredentials) -> IndexingContentResponse:
        response = await self._http_client.get(
            self._url(self._job_path(credentials.job_id, "indexing-context")),
            headers=self._claim_headers(credentials.claim_token),
        )
        self._require_status(response, 200)
        return self._parse_response(response, IndexingContentResponse, "indexing-context")

    async def get_embedding(
            self,
            credentials: ClaimCredentials,
    ) -> EmbeddingResponse:
        response = await self._http_client.get(
            self._url(self._job_path(credentials.job_id, "embedding")),
            headers=self._claim_headers(credentials.claim_token),
            timeout=self._settings.embedding_timeout_seconds,
        )
        self._require_status(response, 200)
        embedding = self._parse_response(
            response,
            EmbeddingResponse,
            "embedding",
        )

        if embedding.job_id != credentials.job_id:
            raise WorkerApiContractError("Embedding identifies a different job")

        return embedding

    async def submit_index_result(
            self,
            credentials: ClaimCredentials,
            result: IndexResultRequest,
    ) -> IndexingJobResponse:
        response = await self._http_client.post(
            self._url(self._job_path(credentials.job_id, "index-result")),
            headers=self._claim_headers(credentials.claim_token),
            json=result.model_dump(by_alias=True, mode="json"),
        )

        if response.status_code not in {200, 201}:
            raise self._api_error(response)

        accepted = self._parse_response(response, IndexingJobResponse, "index-result")

        if accepted.job_id != credentials.job_id:
            raise WorkerApiContractError("Index result identifies a different job")

        return accepted

    async def complete(self, credentials: ClaimCredentials) -> CompletionResponse:
        response = await self._http_client.post(
            self._url(self._job_path(credentials.job_id, "complete")),
            headers=self._claim_headers(credentials.claim_token),
        )

        self._require_status(response, 200)
        completion = self._parse_response(response, CompletionResponse, "complete")

        if completion.job_id != credentials.job_id:
            raise WorkerApiContractError("Completion identifies a different job")

        return completion

    async def fail(self, credentials: ClaimCredentials, failure: FailureRequest) -> FailureResponse:
        response = await self._http_client.post(
            self._url(self._job_path(credentials.job_id, "fail")),
            headers=self._claim_headers(credentials.claim_token),
            json=failure.model_dump(by_alias=True, mode="json"),
        )

        self._require_status(response, 200)
        result = self._parse_response(response, FailureResponse, "fail")

        if result.job_id != credentials.job_id:
            raise WorkerApiContractError("Failure identifies a different job")

        return result

    async def acknowledge_cancellation(self, credentials: ClaimCredentials) -> CancellationResponse:
        response = await self._http_client.post(
            self._url(self._job_path(credentials.job_id, "cancelled")),
            headers=self._claim_headers(credentials.claim_token),
        )

        self._require_status(response, 200)
        result = self._parse_response(response, CancellationResponse, "cancellation")

        if result.job_id != credentials.job_id:
            raise WorkerApiContractError("Cancellation identifies a different job")

        if result.status is not JobStatus.CANCELLED:
            raise WorkerApiContractError("Cancellation response has an invalid status")

        return result
