import os
import re
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlparse


WORKER_ID_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]*$")


@dataclass(frozen=True, slots=True)
class VisionWorkerSettings:
    api_base_url: str
    worker_id: str
    api_token: str
    ollama_base_url: str
    vision_model: str
    prompt_version: str
    poll_min_seconds: float = 1.0
    poll_max_seconds: float = 30.0
    lease_seconds: int = 120
    http_connect_timeout_seconds: float = 10.0
    http_read_timeout_seconds: float = 60.0
    vision_timeout_seconds: float = 300.0
    maximum_output_bytes: int = 1_048_576
    context_tokens: int = 8_192
    maximum_generated_tokens: int = 1_536
    maximum_image_edge_pixels: int = 2_048
    image_jpeg_quality: int = 90
    temporary_root: Path = Path("/tmp/ethnowear-vision-worker")

    @classmethod
    def from_environment(cls) -> "VisionWorkerSettings":
        settings = cls(
            api_base_url=_required("ETHNOWEAR_WORKER_BASE_URL").rstrip("/"),
            worker_id=_required("ETHNOWEAR_WORKER_ID"),
            api_token=_required("ETHNOWEAR_WORKER_API_TOKEN"),
            ollama_base_url=_required("ETHNOWEAR_OLLAMA_BASE_URL").rstrip("/"),
            vision_model=_required("ETHNOWEAR_VISION_MODEL"),
            prompt_version=_required("ETHNOWEAR_VISION_PROMPT_VERSION"),
            poll_min_seconds=_number("ETHNOWEAR_WORKER_POLL_MIN_SECONDS", 1.0),
            poll_max_seconds=_number("ETHNOWEAR_WORKER_POLL_MAX_SECONDS", 30.0),
            lease_seconds=_integer("ETHNOWEAR_WORKER_LEASE_SECONDS", 120),
            http_connect_timeout_seconds=_number(
                "ETHNOWEAR_WORKER_HTTP_CONNECT_TIMEOUT_SECONDS",
                10.0,
            ),
            http_read_timeout_seconds=_number(
                "ETHNOWEAR_WORKER_HTTP_READ_TIMEOUT_SECONDS",
                60.0,
            ),
            vision_timeout_seconds=_number(
                "ETHNOWEAR_VISION_TIMEOUT_SECONDS",
                300.0,
            ),
            maximum_output_bytes=_integer(
                "ETHNOWEAR_VISION_MAXIMUM_OUTPUT_BYTES",
                1_048_576,
            ),
            context_tokens=_integer("ETHNOWEAR_VISION_CONTEXT_TOKENS", 8_192),
            maximum_generated_tokens=_integer(
                "ETHNOWEAR_VISION_MAXIMUM_GENERATED_TOKENS",
                1_536,
            ),
            maximum_image_edge_pixels=_integer(
                "ETHNOWEAR_VISION_MAXIMUM_IMAGE_EDGE_PIXELS",
                2_048,
            ),
            image_jpeg_quality=_integer(
                "ETHNOWEAR_VISION_IMAGE_JPEG_QUALITY",
                90,
            ),
            temporary_root=Path(
                os.getenv(
                    "ETHNOWEAR_WORKER_TEMPORARY_ROOT",
                    "/tmp/ethnowear-vision-worker",
                )
            ),
        )
        settings.validate()
        return settings

    def validate(self) -> None:
        _validate_url("ETHNOWEAR_WORKER_BASE_URL", self.api_base_url)
        _validate_url("ETHNOWEAR_OLLAMA_BASE_URL", self.ollama_base_url)

        if len(self.worker_id) > 150 or not WORKER_ID_PATTERN.fullmatch(self.worker_id):
            raise ValueError("ETHNOWEAR_WORKER_ID has an invalid format")
        if len(self.api_token.encode("utf-8")) < 32:
            raise ValueError(
                "ETHNOWEAR_WORKER_API_TOKEN must contain at least 32 UTF-8 bytes"
            )
        if self.poll_min_seconds <= 0:
            raise ValueError("ETHNOWEAR_WORKER_POLL_MIN_SECONDS must be positive")
        if self.poll_max_seconds < self.poll_min_seconds:
            raise ValueError(
                "ETHNOWEAR_WORKER_POLL_MAX_SECONDS must be greater than or equal to the minimum"
            )
        if self.lease_seconds <= 0:
            raise ValueError("ETHNOWEAR_WORKER_LEASE_SECONDS must be positive")
        if self.http_connect_timeout_seconds <= 0:
            raise ValueError(
                "ETHNOWEAR_WORKER_HTTP_CONNECT_TIMEOUT_SECONDS must be positive"
            )
        if self.http_read_timeout_seconds <= 0:
            raise ValueError(
                "ETHNOWEAR_WORKER_HTTP_READ_TIMEOUT_SECONDS must be positive"
            )
        if self.vision_timeout_seconds <= 0:
            raise ValueError("ETHNOWEAR_VISION_TIMEOUT_SECONDS must be positive")
        if self.maximum_output_bytes <= 0:
            raise ValueError("ETHNOWEAR_VISION_MAXIMUM_OUTPUT_BYTES must be positive")
        if self.context_tokens < 8_192:
            raise ValueError("ETHNOWEAR_VISION_CONTEXT_TOKENS must be at least 8192")
        if self.maximum_generated_tokens <= 0:
            raise ValueError(
                "ETHNOWEAR_VISION_MAXIMUM_GENERATED_TOKENS must be positive"
            )
        if self.maximum_image_edge_pixels < 1_024:
            raise ValueError(
                "ETHNOWEAR_VISION_MAXIMUM_IMAGE_EDGE_PIXELS must be at least 1024"
            )
        if not 70 <= self.image_jpeg_quality <= 100:
            raise ValueError(
                "ETHNOWEAR_VISION_IMAGE_JPEG_QUALITY must be between 70 and 100"
            )
        if not self.temporary_root.is_absolute():
            raise ValueError("ETHNOWEAR_WORKER_TEMPORARY_ROOT must be an absolute path")


def _required(name: str) -> str:
    value = os.getenv(name)

    if value is None or not value.strip():
        raise ValueError(f"{name} is required")

    return value.strip()


def _number(name: str, default: float) -> float:
    value = os.getenv(name)

    if value is None or not value.strip():
        return default

    try:
        return float(value)
    except ValueError as error:
        raise ValueError(f"{name} must be a number") from error


def _integer(name: str, default: int) -> int:
    value = os.getenv(name)

    if value is None or not value.strip():
        return default

    try:
        return int(value)
    except ValueError as error:
        raise ValueError(f"{name} must be an integer") from error


def _validate_url(name: str, value: str) -> None:
    parsed = urlparse(value)

    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        raise ValueError(f"{name} must be a valid HTTP or HTTPS URL")
