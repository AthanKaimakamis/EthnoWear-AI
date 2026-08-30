import json
from pathlib import Path

import httpx

from ethnowear_figure_worker.api.models import (
    CancellationResponse,
    ClaimRequest,
    ClaimResponse,
    CompletionResponse,
    FailureRequest,
    FailureResponse,
    FigureCropRequest,
    FigureCropResponse,
    FigureExtractionContextResponse,
    HeartbeatRequest,
    HeartbeatResponse,
    JobStatus,
)
from ethnowear_figure_worker.config import FigureWorkerSettings
from ethnowear_worker_common.api.client import WorkerApiTransport
from ethnowear_worker_common.api.errors import WorkerApiContractError
from ethnowear_worker_common.input.image import download_controlled_image
from ethnowear_worker_common.jobs.context import ClaimCredentials


class FigureWorkerApiClient(WorkerApiTransport):
    CLAIM_PATH = f"{WorkerApiTransport.JOBS_PATH}/claim"

    def __init__(
            self,
            settings: FigureWorkerSettings,
            http_client: httpx.AsyncClient,
    ) -> None:
        super().__init__(
            settings.api_base_url,
            settings.worker_id,
            settings.api_token,
            http_client
        )
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
            self._url(self._job_path(
                credentials.job_id,
                "heartbeat",
            )),
            headers=self._claim_headers(credentials.claim_token),
            json=request.model_dump(by_alias=True, mode="json"),
        )
        self._require_status(response, 200)
        return self._parse_response(
            response,
            HeartbeatResponse,
            "heartbeat",
        )

    async def get_context(
            self,
            credentials: ClaimCredentials
    ) -> FigureExtractionContextResponse:
        response = await self._http_client.get(
            self._url(self._job_path(
                credentials.job_id,
                "figure-extraction/context",
            )),
            headers=self._claim_headers(credentials.claim_token),
        )
        self._require_status(response, 200)
        context = self._parse_response(
            response,
            FigureExtractionContextResponse,
            "figure-extraction context",
        )

        if context.job_id != credentials.job_id:
            raise WorkerApiContractError("Figure context identifies a different job")

        return context

    async def download_input(
            self,
            credentials: ClaimCredentials,
            destination: Path,
            maximum_bytes: int,
    ) -> int:
        return await download_controlled_image(
            http_client=self._http_client,
            url=self._url(self._job_path(
                credentials.job_id,
                "input",
            )),
            headers=self._claim_headers(credentials.claim_token),
            destination=destination,
            maximum_bytes=maximum_bytes,
        )

    async def upload_figure(
            self,
            credentials: ClaimCredentials,
            metadata: FigureCropRequest,
            crop_path: Path,
            *,
            content_type: str,
    ) -> FigureCropResponse:
        metadata_json = json.dumps(
            metadata.model_dump(
                by_alias=True,
                mode="json",
                exclude_none=True,
            ),
            ensure_ascii=False,
            separators=(",", ":"),
        )

        with crop_path.open("rb") as crop:
            response = await self._http_client.post(
                self._url(self._job_path(
                    credentials.job_id,
                    "figures",
                )),
                headers=self._claim_headers(credentials.claim_token),
                files={
                    "file": (
                        crop_path.name,
                        crop,
                        content_type,
                    ),
                    "metadata": (
                        None,
                        metadata_json,
                        "application/json",
                    ),
                },
            )

        if response.status_code not in {200, 201}:
            raise self._api_error(response)

        result = self._parse_response(
            response,
            FigureCropResponse,
            "figure crop",
        )

        if result.document_page_id <= 0:
            raise WorkerApiContractError("Figure response has an invalid page identity")

        if result.figure_ordinal != metadata.figure_ordinal:
            raise WorkerApiContractError("Figure response ordinal does not match the request")

        return result

    async def complete(
            self,
            credentials: ClaimCredentials,
    ) -> CompletionResponse:
        response = await self._http_client.post(
            self._url(self._job_path(
                credentials.job_id,
                "complete",
            )),
            headers=self._claim_headers(credentials.claim_token),
        )
        self._require_status(response, 200)
        result = self._parse_response(
            response,
            CompletionResponse,
            "completion",
        )

        if result.job_id != credentials.job_id:
            raise WorkerApiContractError("Completion identifies a different job")

        return result

    async def fail(
            self,
            credentials: ClaimCredentials,
            failure: FailureRequest,
    ) -> FailureResponse:
        response = await self._http_client.post(
            self._url(self._job_path(
                credentials.job_id,
                "fail",
            )),
            headers=self._claim_headers(credentials.claim_token),
            json=failure.model_dump(by_alias=True, mode="json"),
        )
        self._require_status(response, 200)
        result = self._parse_response(
            response,
            FailureResponse,
            "failure",
        )

        if result.job_id != credentials.job_id:
            raise WorkerApiContractError("Failure identifies a different job")

        return result

    async def acknowledge_cancellation(
            self,
            credentials: ClaimCredentials,
    ) -> CancellationResponse:
        response = await self._http_client.post(
            self._url(self._job_path(
                credentials.job_id,
                "cancelled",
            )),
            headers=self._claim_headers(credentials.claim_token),
        )
        self._require_status(response, 200)
        result = self._parse_response(
            response,
            CancellationResponse,
            "cancellation",
        )

        if result.job_id != credentials.job_id:
            raise WorkerApiContractError("Cancellation response identifies a different job")

        if result.status is not JobStatus.CANCELLED:
            raise WorkerApiContractError("Cancellation response has an invalid status")

        return result
