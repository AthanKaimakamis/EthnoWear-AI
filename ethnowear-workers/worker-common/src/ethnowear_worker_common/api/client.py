from typing import TypeVar

import httpx
from pydantic import BaseModel, SecretStr, ValidationError

from ethnowear_worker_common.api.errors import (
    WorkerApiContractError,
    WorkerApiRequestError,
)
from ethnowear_worker_common.api.models import ReadinessResponse


ResponseModel = TypeVar("ResponseModel", bound=BaseModel)


class WorkerApiTransport:
    HEALTH_PATH = "/api/internal/worker/health"
    JOBS_PATH = "/api/internal/worker/jobs"

    def __init__(
        self,
        api_base_url: str,
        worker_id: str,
        api_token: str,
        http_client: httpx.AsyncClient,
    ) -> None:
        self._api_base_url = api_base_url.rstrip("/")
        self._worker_id = worker_id
        self._api_token = api_token
        self._http_client = http_client

    async def health(self) -> ReadinessResponse:
        response = await self._http_client.get(
            self._url(self.HEALTH_PATH),
            headers=self._service_headers(),
        )

        self._require_status(response, 200)
        return self._parse_response(
            response,
            ReadinessResponse,
            "readiness",
        )

    def _url(self, path: str) -> str:
        return f"{self._api_base_url}/{path.lstrip('/')}"

    def _job_path(self, job_id: int, suffix: str) -> str:
        return f"{self.JOBS_PATH}/{job_id}/{suffix.lstrip('/')}"

    def _service_headers(self) -> dict[str, str]:
        return {"Authorization": f"Worker {self._api_token}"}

    def _claim_headers(self, claim_token: SecretStr) -> dict[str, str]:
        return {
            **self._service_headers(),
            "X-Worker-Id": self._worker_id,
            "X-Worker-Claim-Token": claim_token.get_secret_value(),
        }

    @staticmethod
    def _require_status(
        response: httpx.Response,
        expected_status: int,
    ) -> None:
        if response.status_code != expected_status:
            raise WorkerApiTransport._api_error(response)

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
