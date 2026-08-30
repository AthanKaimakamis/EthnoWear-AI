import os
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlparse


@dataclass(frozen=True, slots=True)
class QualityWorkerSettings:
    api_base_url: str
    worker_id: str
    api_token: str
    poll_min_seconds: float = 1.0
    poll_max_seconds: float = 30.0
    lease_seconds: int = 120
    temporary_root: Path = Path("/tmp/ethnowear-quality-worker")
    http_connect_timeout_seconds: float = 10.0
    http_read_timeout_seconds: float = 60.0
    bulgarian_dictionary_path: Path = Path("/usr/share/hunspell/bg_BG.dic")

    @classmethod
    def from_environment(cls) -> "QualityWorkerSettings":
        settings = cls(
            api_base_url=_required("ETHNOWEAR_WORKER_BASE_URL").rstrip("/"),
            worker_id=_required("ETHNOWEAR_WORKER_ID"),
            api_token=_required("ETHNOWEAR_WORKER_API_TOKEN"),
            poll_min_seconds=_float("ETHNOWEAR_WORKER_POLL_MIN_SECONDS", 1.0),
            poll_max_seconds=_float("ETHNOWEAR_WORKER_POLL_MAX_SECONDS", 30.0),
            lease_seconds=_int("ETHNOWEAR_WORKER_LEASE_SECONDS", 120),
            temporary_root=Path(os.getenv(
                "ETHNOWEAR_WORKER_TEMPORARY_ROOT",
                "/tmp/ethnowear-quality-worker",
            )),
            http_connect_timeout_seconds=_float(
                "ETHNOWEAR_WORKER_HTTP_CONNECT_TIMEOUT_SECONDS", 10.0
            ),
            http_read_timeout_seconds=_float(
                "ETHNOWEAR_WORKER_HTTP_READ_TIMEOUT_SECONDS", 60.0
            ),
            bulgarian_dictionary_path=Path(os.getenv(
                "ETHNOWEAR_WORKER_BULGARIAN_DICTIONARY_PATH",
                "/usr/share/hunspell/bg_BG.dic",
            )),
        )
        settings.validate()
        return settings

    def validate(self) -> None:
        parsed = urlparse(self.api_base_url)
        if parsed.scheme not in {"http", "https"} or not parsed.netloc:
            raise ValueError("ETHNOWEAR_WORKER_BASE_URL must be a valid HTTP or HTTPS URL")
        if not self.worker_id:
            raise ValueError("ETHNOWEAR_WORKER_ID must not be empty")
        if len(self.api_token.encode("utf-8")) < 32:
            raise ValueError("ETHNOWEAR_WORKER_API_TOKEN must contain at least 32 UTF-8 bytes")
        if self.poll_min_seconds <= 0:
            raise ValueError("ETHNOWEAR_WORKER_POLL_MIN_SECONDS must be positive")
        if self.poll_max_seconds < self.poll_min_seconds:
            raise ValueError("ETHNOWEAR_WORKER_POLL_MAX_SECONDS must not be smaller than the minimum")
        if self.lease_seconds <= 0:
            raise ValueError("ETHNOWEAR_WORKER_LEASE_SECONDS must be positive")
        if not self.temporary_root.is_absolute():
            raise ValueError("ETHNOWEAR_WORKER_TEMPORARY_ROOT must be absolute")
        if self.http_connect_timeout_seconds <= 0 or self.http_read_timeout_seconds <= 0:
            raise ValueError("Worker HTTP timeouts must be positive")
        if not self.bulgarian_dictionary_path.is_absolute():
            raise ValueError("ETHNOWEAR_WORKER_BULGARIAN_DICTIONARY_PATH must be absolute")


def _required(name: str) -> str:
    value = os.getenv(name, "").strip()
    if not value:
        raise ValueError(f"{name} is required")
    return value


def _float(name: str, default: float) -> float:
    try:
        return float(os.getenv(name, str(default)))
    except ValueError as error:
        raise ValueError(f"{name} must be a number") from error


def _int(name: str, default: int) -> int:
    try:
        return int(os.getenv(name, str(default)))
    except ValueError as error:
        raise ValueError(f"{name} must be an integer") from error
