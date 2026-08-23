import os
from dataclasses import dataclass
from urllib.parse import urlparse
from pathlib import Path


@dataclass(frozen=True, slots=True)
class WorkerSettings:
    base_url: str
    worker_id: str
    api_token: str
    poll_min_seconds: float
    poll_max_seconds: float
    temporary_root: Path = Path("/tmp/ethnowear-worker")
    http_connect_timeout_seconds: float = 10.0
    http_read_timeout_seconds: float = 60.0

    @classmethod
    def from_environment(cls) -> "WorkerSettings":
        settings = cls(
            base_url=_required_environment_variable(
                "ETHNOWEAR_WORKER_BASE_URL"
            ).rstrip("/"),
            worker_id=_required_environment_variable(
                "ETHNOWEAR_WORKER_ID"
            ),
            api_token=_required_environment_variable(
                "ETHNOWEAR_WORKER_API_TOKEN"
            ),
            poll_min_seconds=_float_environment_variable(
                "ETHNOWEAR_WORKER_POLL_MIN_SECONDS",
                default=1.0,
            ),
            poll_max_seconds=_float_environment_variable(
                "ETHNOWEAR_WORKER_POLL_MAX_SECONDS",
                default=30.0,
            ),
            temporary_root=Path(
                os.getenv(
                    "ETHNOWEAR_WORKER_TEMPORARY_ROOT",
                    "/tmp/ethnowear-worker",
                )
            ),
            http_connect_timeout_seconds=_float_environment_variable(
                "ETHNOWEAR_WORKER_HTTP_CONNECT_TIMEOUT_SECONDS",
                default=10.0,
            ),
            http_read_timeout_seconds=_float_environment_variable(
                "ETHNOWEAR_WORKER_HTTP_READ_TIMEOUT_SECONDS",
                default=60.0,
            )
        )

        settings.validate()
        return settings

    def validate(self) -> None:
        parsed_url = urlparse(self.base_url)

        if parsed_url.scheme not in {"http", "https"} or not parsed_url.netloc:
            raise ValueError("ETHNOWEAR_WORKER_BASE_URL must be a valid HTTP or HTTPS URL")

        if not self.worker_id:
            raise ValueError("ETHNOWEAR_WORKER_ID must not be empty")

        if len(self.api_token.encode("utf-8")) < 32:
            raise ValueError("ETHNOWEAR_WORKER_API_TOKEN must contain at least 32 UTF-8 bytes")

        if self.poll_min_seconds <= 0:
            raise ValueError("ETHNOWEAR_WORKER_POLL_MIN_SECONDS must be positive")

        if self.poll_max_seconds < self.poll_min_seconds:
            raise ValueError(
                "ETHNOWEAR_WORKER_POLL_MAX_SECONDS must be greater than "
                "or equal to ETHNOWEAR_WORKER_POLL_MIN_SECONDS"
            )

        if not self.temporary_root.is_absolute():
            raise ValueError("ETHNOWEAR_WORKER_TEMPORARY_ROOT must be an absolute path")

        if self.http_connect_timeout_seconds <= 0:
            raise ValueError("ETHNOWEAR_WORKER_HTTP_CONNECT_TIMEOUT_SECONDS must be positive")

        if self.http_read_timeout_seconds <= 0:
            raise ValueError("ETHNOWEAR_WORKER_HTTP_READ_TIMEOUT_SECONDS must be positive")


def _required_environment_variable(name: str) -> str:
    value = os.getenv(name)

    if value is None or not value.strip():
        raise ValueError(f"{name} is required")

    return value.strip()


def _float_environment_variable(name: str, default: float) -> float:
    raw_value = os.getenv(name)

    if raw_value is None or not raw_value.strip():
        return default

    try:
        return float(raw_value)
    except ValueError as error:
        raise ValueError(f"{name} must be a number") from error
