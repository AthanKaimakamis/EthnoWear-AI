import httpx
from typing import TypeVar
from pydantic import BaseModel, SecretStr, ValidationError
from pathlib import Path
from urllib.parse import urljoin, urlparse

from ethnowear_worker.api.errors import (
    WorkerApiContractError,
    WorkerApiRequestError,
    WorkerInputError
)
from ethnowear_worker.api.models import (
    ClaimRequest, ClaimResponse,
    HeartbeatRequest, HeartbeatResponse,
    ManifestRequest, ManifestResponse,
    RenditionMetadata, RenditionResponse,
    JobStatus,
    CompletionResponse,
    FailureRequest, FailureResponse,
    CancellationResponse, ReadinessResponse
)
from ethnowear_worker.jobs.context import ClaimCredentials
from ethnowear_worker.config import WorkerSettings
from ethnowear_worker.pdf.renderer import RenderedPage

ResponseModel = TypeVar("ResponseModel", bound=BaseModel)


class WorkerApiClient:
    HEALTH_PATH = "/api/internal/worker/health"
    JOBS_PATH = "/api/internal/worker/jobs"
    CLAIM_PATH = f"{JOBS_PATH}/claim"

    def __init__(self, settings: WorkerSettings, http_client: httpx.AsyncClient) -> None:
        self._settings = settings
        self._http_client = http_client

    async def health(self) -> ReadinessResponse:
        response = await self._http_client.get(
            self._url(self.HEALTH_PATH),
            headers=self._service_headers(),
        )

        self._require_status(response, 200)
        return self._parse_response(response, ReadinessResponse, "readiness")

    # region claims ----------------------------------------------------------------
    async def claim(self) -> ClaimResponse | None:
        request = ClaimRequest(worker_id=self._settings.worker_id)
        response = await self._http_client.post(
            self._url(self.CLAIM_PATH),
            headers=self._service_headers(),
            json=request.model_dump(by_alias=True, mode="json"),
        )

        if response.status_code == 204:
            return None

        self._require_status(response, 200)
        return self._parse_response(response, ClaimResponse, "claim")

    # endregion

    # region heartbeat --------------------------------------------------------------
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

    # endregion

    # region download_input ---------------------------------------------------------
    async def download_input(
            self,
            credentials: ClaimCredentials,
            destination: Path,
            maximum_bytes: int
    ) -> int:
        if maximum_bytes <= 0:
            raise ValueError("Maximum input size must be positive")

        async with self._http_client.stream(
                "GET",
                self._url(self._job_path(credentials.job_id, "input")),
                headers=self._claim_headers(credentials.claim_token),
                follow_redirects=False,
        ) as response:
            if response.status_code != 302:
                return await self._write_pdf(
                    response,
                    destination,
                    maximum_bytes,
                )

            redirect_url = self._redirect_url(response)

        # Claim credentials are deliberately not forwarded.
        async with self._http_client.stream(
                "GET",
                redirect_url,
                follow_redirects=False,
        ) as response:
            return await self._write_pdf(
                response,
                destination,
                maximum_bytes,
            )

    # endregion

    # region submit_manifest ----------------------------------------------------------------
    async def submit_manifest(
            self,
            credentials: ClaimCredentials,
            manifest: ManifestRequest
    ) -> ManifestResponse:
        response = await self._http_client.post(
            self._url(self._job_path(credentials.job_id, "page-extraction/manifest")),
            headers=self._claim_headers(credentials.claim_token),
            json=manifest.model_dump(by_alias=True, mode="json"),
        )

        self._require_status(response, 200)

        return self._parse_response(
            response,
            ManifestResponse,
            "manifest",
        )

    # endregion

    # region upload_rendition ----------------------------------------------------------------
    async def upload_rendition(
            self,
            credentials: ClaimCredentials,
            page_id: int,
            metadata: RenditionMetadata,
            rendered: RenderedPage
    ) -> RenditionResponse:
        if page_id <= 0:
            raise ValueError("Page ID must be positive")

        with rendered.path.open("rb") as image_file:
            response = await self._http_client.post(
                self._url(
                    self._job_path(
                        credentials.job_id,
                        f"pages/{page_id}/renditions",
                    )
                ),
                headers=self._claim_headers(credentials.claim_token),
                files={
                    "file": (
                        rendered.path.name,
                        image_file,
                        rendered.mime_type,
                    ),
                    "metadata": (
                        None,
                        metadata.model_dump_json(by_alias=True),
                        "application/json",
                    )
                }
            )

            if response.status_code not in (200, 201):
                raise self._api_error(response)

            result = self._parse_response(
                response,
                RenditionResponse,
                "rendition upload"
            )

            if result.page_id != page_id:
                raise WorkerApiContractError("Rendition response identifies a different page")

            if result.rendition_type != metadata.rendition_type:
                raise WorkerApiContractError("Rendition response identifies a different type")

            return result

    # endregion

    # region complete ----------------------------------------------------------------
    async def complete(self, credentials: ClaimCredentials) -> CompletionResponse:
        response = await self._http_client.post(
            self._url(self._job_path(credentials.job_id, "complete")),
            headers=self._claim_headers(credentials.claim_token),
        )

        self._require_status(response, 200)

        result = self._parse_response(
            response,
            CompletionResponse,
            "completion",
        )

        if result.job_id != credentials.job_id:
            raise WorkerApiContractError("Completion response identifies a different job")

        return result

    # endregion

    # region fail ----------------------------------------------------------------
    async def fail(
            self,
            credentials: ClaimCredentials,
            failure: FailureRequest
    ) -> FailureResponse:
        response = await self._http_client.post(
            self._url(self._job_path(credentials.job_id, "fail")),
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
            raise WorkerApiContractError("Failure response identifies a different job")

        return result

    # endregion

    # region acknowledge_cancellation -----------------------------------------------
    async def acknowledge_cancellation(
            self,
            credentials: ClaimCredentials
    ) -> CancellationResponse:
        response = await self._http_client.post(
            self._url(self._job_path(credentials.job_id, "cancelled")),
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

        if result.status != JobStatus.CANCELLED:
            raise WorkerApiContractError("Cancellation response has an invalid status")

        return result

    # endregion

    def _url(self, path: str) -> str:
        return f"{self._settings.base_url}/{path.lstrip('/')}"

    def _job_path(self, job_id: int, suffix: str) -> str:
        return f"{self.JOBS_PATH}/{job_id}/{suffix.lstrip('/')}"

    def _service_headers(self) -> dict[str, str]:
        return {"Authorization": f"Worker {self._settings.api_token}"}

    def _claim_headers(self, claim_token: SecretStr) -> dict[str, str]:
        return {
            **self._service_headers(),
            "X-Worker-Id": self._settings.worker_id,
            "X-Worker-Claim-Token": claim_token.get_secret_value(),
        }

    @staticmethod
    def _require_status(
            response: httpx.Response,
            expected_status: int,
    ) -> None:
        if response.status_code != expected_status:
            raise WorkerApiClient._api_error(response)

    @staticmethod
    def _parse_response(
            response: httpx.Response,
            model_type: type[ResponseModel],
            operation: str,
    ) -> ResponseModel:
        try:
            return model_type.model_validate(response.json())
        except (ValueError, ValidationError) as error:
            raise WorkerApiContractError(
                f"Worker API returned an invalid {operation} response"
            ) from error

    @staticmethod
    def _api_error(response: httpx.Response) -> WorkerApiRequestError:
        code = f"HTTP_{response.status_code}"
        message = "The worker API request failed"

        try:
            body = response.json()
            if isinstance(body, dict):
                if isinstance(body.get("code"), str):
                    code = body["code"]
                if isinstance(body.get("message"), str):
                    message = body["message"]
        except ValueError:
            pass

        return WorkerApiRequestError(
            status_code=response.status_code,
            code=code,
            safe_message=message,
        )

    @staticmethod
    def _redirect_url(response: httpx.Response) -> str:
        location = response.headers.get("Location")

        if not location:
            raise WorkerInputError("Input redirect did not provide a location")

        redirect_url = urljoin(str(response.request.url), location)
        parsed = urlparse(redirect_url)

        if parsed.scheme not in {"http", "https"} or not parsed.netloc:
            raise WorkerInputError("Input redirect location is invalid")

        return redirect_url

    async def _write_pdf(
            self,
            response: httpx.Response,
            destination: Path,
            maximum_bytes: int
    ) -> int:
        if response.status_code != 200:
            await response.aread()

        self._require_status(response, 200)

        content_type = response.headers.get(
            "Content-Type",
            ""
        ).partition(";")[0].strip().lower()

        if content_type != "application/pdf":
            raise WorkerInputError("Input response does not have PDF content type")

        content_length = response.headers.get("Content-Length")
        total_bytes = 0

        if content_length is not None:
            try:
                declared_size = int(content_length)
            except ValueError as error:
                raise WorkerInputError("Input response has invalid content length") from error

            if declared_size <= 0 or declared_size > maximum_bytes:
                raise WorkerInputError("Input exceeds the maximum size")

        try:
            with destination.open("wb") as output:
                destination.chmod(0o600)

                async for chunk in response.aiter_bytes():
                    total_bytes += len(chunk)

                    if total_bytes > maximum_bytes:
                        raise WorkerInputError("Input exceeds the maximum size")

                    output.write(chunk)

            if total_bytes == 0:
                raise WorkerInputError("Input PDF is empty")

            with destination.open("rb") as input_file:
                if input_file.read(5) != b"%PDF-":
                    raise WorkerInputError("Input does not contain a PDF signature")

            return total_bytes
        except BaseException:
            destination.unlink(missing_ok=True)
            raise
