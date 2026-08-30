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
    temporary_root: Path = Path("/tmp/ethnowear-document-worker")
    http_connect_timeout_seconds: float = 10.0
    http_read_timeout_seconds: float = 60.0
    tesseract_binary: str = "tesseract"
    ocr_language: str = "bul+eng"
    ocr_oem: int = 1
    ocr_psm: int = 3
    ocr_timeout_seconds: int = 300
    ocr_maximum_layout_regions: int = 32

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
                    "/tmp/ethnowear-document-worker",
                )
            ),
            http_connect_timeout_seconds=_float_environment_variable(
                "ETHNOWEAR_WORKER_HTTP_CONNECT_TIMEOUT_SECONDS",
                default=10.0,
            ),
            http_read_timeout_seconds=_float_environment_variable(
                "ETHNOWEAR_WORKER_HTTP_READ_TIMEOUT_SECONDS",
                default=60.0,
            ),
            tesseract_binary=os.getenv(
                "ETHNOWEAR_WORKER_TESSERACT_BINARY",
                "tesseract",
            ).strip(),
            ocr_language=os.getenv(
                "ETHNOWEAR_WORKER_OCR_LANGUAGE",
                "bul+eng",
            ).strip(),
            ocr_oem=_int_environment_variable(
                "ETHNOWEAR_WORKER_OCR_OEM",
                default=1,
            ),
            ocr_psm=_int_environment_variable(
                "ETHNOWEAR_WORKER_OCR_PSM",
                default=3,
            ),
            ocr_timeout_seconds=_int_environment_variable(
                "ETHNOWEAR_WORKER_OCR_TIMEOUT_SECONDS",
                default=300,
            ),
            ocr_maximum_layout_regions=_int_environment_variable(
                "ETHNOWEAR_WORKER_OCR_MAXIMUM_LAYOUT_REGIONS",
                default=32,
            ),
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

        if not self.tesseract_binary:
            raise ValueError("ETHNOWEAR_WORKER_TESSERACT_BINARY must not be empty")

        languages = self.ocr_language.split("+")

        if (not self.ocr_language or any(
                not language.isalpha()
                or len(language) < 3
                or len(language) > 10
                for language in languages)):
            raise ValueError("ETHNOWEAR_WORKER_OCR_LANGUAGE is invalid")

        if self.ocr_oem < 0 or self.ocr_oem > 3:
            raise ValueError("ETHNOWEAR_WORKER_OCR_OEM must be between 0 and 3")

        if self.ocr_psm < 0 or self.ocr_psm > 13:
            raise ValueError("ETHNOWEAR_WORKER_OCR_PSM must be between 0 and 13")

        if self.ocr_timeout_seconds <= 0:
            raise ValueError("ETHNOWEAR_WORKER_OCR_TIMEOUT_SECONDS must be positive")

        if not 1 <= self.ocr_maximum_layout_regions <= 100:
            raise ValueError("ETHNOWEAR_WORKER_OCR_MAXIMUM_LAYOUT_REGIONS must be between 1 and 100")


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


def _int_environment_variable(name: str, default: int) -> int:
    raw_value = os.getenv(name)

    if raw_value is None or not raw_value.strip():
        return default

    try:
        return int(raw_value)
    except ValueError as error:
        raise ValueError(f"{name} must be an integer") from error
