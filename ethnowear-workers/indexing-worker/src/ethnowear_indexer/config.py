import os
from dataclasses import dataclass
from urllib.parse import urlparse
from pathlib import Path

@dataclass
class IndexerSettings:
    api_base_url: str
    worker_id: str
    api_token: str
    qdrant_url: str
    qdrant_collection: str
    poll_min_seconds: float = 1.0
    poll_max_seconds: float = 30.0
    lease_seconds: int = 120
    http_connect_timeout_seconds: float = 10.0
    http_read_timeout_seconds: float = 60.0
    embedding_timeout_seconds: float = 120.0
    qdrant_timeout_seconds: float = 30.0
    temporary_root: Path = Path("/tmp/ethnowear-indexing-worker")

    @classmethod
    def from_env(cls) -> "IndexerSettings":
        settings = cls(
            api_base_url=_required("ETHNOWEAR_WORKER_BASE_URL").rstrip("/"),
            worker_id=_required("ETHNOWEAR_WORKER_ID"),
            api_token=_required("ETHNOWEAR_WORKER_API_TOKEN"),
            qdrant_url=_required("ETHNOWEAR_QDRANT_URL").rstrip("/"),
            qdrant_collection=_required("ETHNOWEAR_QDRANT_COLLECTION"),
            poll_min_seconds=_number(
                "ETHNOWEAR_WORKER_POLL_MIN_SECONDS",
                default=1.0,
            ),
            poll_max_seconds=_number(
                "ETHNOWEAR_WORKER_POLL_MAX_SECONDS",
                default=30.0,
            ),
            lease_seconds=_integer(
                "ETHNOWEAR_WORKER_LEASE_SECONDS",
                default=120,
            ),
            http_connect_timeout_seconds=_number(
                "ETHNOWEAR_WORKER_HTTP_CONNECT_TIMEOUT_SECONDS",
                default=10.0,
            ),
            http_read_timeout_seconds=_number(
                "ETHNOWEAR_WORKER_HTTP_READ_TIMEOUT_SECONDS",
                default=60.0,
            ),
            embedding_timeout_seconds=_number(
                "ETHNOWEAR_EMBEDDING_TIMEOUT_SECONDS",
                default=120.0,
            ),
            qdrant_timeout_seconds=_number(
                "ETHNOWEAR_QDRANT_TIMEOUT_SECONDS",
                default=30.0,
            ),
            temporary_root=Path(
                os.getenv(
                    "ETHNOWEAR_WORKER_TEMPORARY_ROOT",
                    "/tmp/ethnowear-indexing-worker",
                )
            ),
        )

        settings.validate()
        return settings

    def validate(self) -> None:
        _validate_url("ETHNOWEAR_WORKER_BASE_URL", self.api_base_url)
        _validate_url("ETHNOWEAR_QDRANT_URL", self.qdrant_url)

        if not self.worker_id:
            raise ValueError("ETHNOWEAR_WORKER_ID must not be empty")
        if len(self.api_token.encode("utf-8")) < 32:
            raise ValueError("ETHNOWEAR_WORKER_API_TOKEN must contain at least 32 UTF-8 bytes")
        if not self.qdrant_collection:
            raise ValueError("ETHNOWEAR_QDRANT_COLLECTION must not be empty")
        if self.poll_min_seconds <= 0:
            raise ValueError("ETHNOWEAR_WORKER_POLL_MIN_SECONDS must be positive")
        if self.poll_max_seconds < self.poll_min_seconds:
            raise ValueError("ETHNOWEAR_WORKER_POLL_MAX_SECONDS must be greater than or equal to the minimum")
        if self.lease_seconds <= 0:
            raise ValueError("ETHNOWEAR_WORKER_LEASE_SECONDS must be positive")
        if self.http_connect_timeout_seconds <= 0:
            raise ValueError("ETHNOWEAR_WORKER_HTTP_CONNECT_TIMEOUT_SECONDS must be positive")
        if self.http_read_timeout_seconds <= 0:
            raise ValueError("ETHNOWEAR_WORKER_HTTP_READ_TIMEOUT_SECONDS must be positive")
        if self.embedding_timeout_seconds <= 0:
            raise ValueError("ETHNOWEAR_EMBEDDING_TIMEOUT_SECONDS must be positive")
        if self.qdrant_timeout_seconds <= 0:
            raise ValueError("ETHNOWEAR_QDRANT_TIMEOUT_SECONDS must be positive")
        if not self.temporary_root.is_absolute():
            raise ValueError("ETHNOWEAR_WORKER_TEMPORARY_ROOT must be an absolute path")


def _required(name: str) -> str:
    value = os.getenv(name)

    if value is None or not value.strip():
        raise ValueError(f"{name} is required")

    return value.strip()

def _number(name: str, default: float) -> float:
    value = os.environ.get(name)

    if value is None or not value.strip(): return default

    try:
        return float(value)
    except ValueError as error:
        raise ValueError(f"{name} must be a number") from error

def _integer(name: str, default: int) -> int:
    value = os.getenv(name)

    if value is None or not value.strip(): return default

    try:
        return int(value)
    except ValueError as error:
        raise ValueError(f"{name} must be an integer") from error


def _validate_url(name: str, value: str) -> None:
    parsed = urlparse(value)

    if parsed.scheme not in { "http", "https" } or not parsed.netloc:
        raise ValueError(f"{name} must be a valid HTTP or HTTPS URL")
