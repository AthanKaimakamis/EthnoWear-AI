from pathlib import Path

import pytest

from ethnowear_vision_worker.config import VisionWorkerSettings


REQUIRED_ENVIRONMENT = {
    "ETHNOWEAR_WORKER_BASE_URL": "http://localhost:8080/",
    "ETHNOWEAR_WORKER_ID": "vision-worker-1",
    "ETHNOWEAR_WORKER_API_TOKEN": "x" * 32,
    "ETHNOWEAR_OLLAMA_BASE_URL": "http://localhost:11434/",
    "ETHNOWEAR_VISION_MODEL": "gemma3:4b",
    "ETHNOWEAR_VISION_PROMPT_VERSION": "vision-ocr-v2",
}


def configure_environment(monkeypatch: pytest.MonkeyPatch) -> None:
    for name, value in REQUIRED_ENVIRONMENT.items():
        monkeypatch.setenv(name, value)


def test_loads_required_values_and_defaults(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    configure_environment(monkeypatch)

    settings = VisionWorkerSettings.from_environment()

    assert settings.api_base_url == "http://localhost:8080"
    assert settings.worker_id == "vision-worker-1"
    assert settings.ollama_base_url == "http://localhost:11434"
    assert settings.vision_model == "gemma3:4b"
    assert settings.prompt_version == "vision-ocr-v2"
    assert settings.poll_min_seconds == 1.0
    assert settings.poll_max_seconds == 30.0
    assert settings.lease_seconds == 120
    assert settings.vision_timeout_seconds == 300.0
    assert settings.maximum_output_bytes == 1_048_576
    assert settings.context_tokens == 8_192
    assert settings.maximum_generated_tokens == 1_536
    assert settings.maximum_image_edge_pixels == 2_048
    assert settings.image_jpeg_quality == 90
    assert settings.temporary_root == Path("/tmp/ethnowear-vision-worker")


@pytest.mark.parametrize("missing_name", REQUIRED_ENVIRONMENT)
def test_rejects_missing_required_values(
    monkeypatch: pytest.MonkeyPatch,
    missing_name: str,
) -> None:
    configure_environment(monkeypatch)
    monkeypatch.delenv(missing_name)

    with pytest.raises(ValueError, match=f"^{missing_name} is required$"):
        VisionWorkerSettings.from_environment()


@pytest.mark.parametrize(
    ("name", "value", "message"),
    [
        ("ETHNOWEAR_WORKER_BASE_URL", "localhost:8080", "valid HTTP or HTTPS URL"),
        ("ETHNOWEAR_OLLAMA_BASE_URL", "ftp://localhost", "valid HTTP or HTTPS URL"),
        ("ETHNOWEAR_WORKER_ID", "invalid worker", "invalid format"),
        ("ETHNOWEAR_WORKER_API_TOKEN", "short", "at least 32 UTF-8 bytes"),
        ("ETHNOWEAR_WORKER_POLL_MIN_SECONDS", "0", "must be positive"),
        ("ETHNOWEAR_WORKER_POLL_MAX_SECONDS", "0.5", "greater than or equal"),
        ("ETHNOWEAR_WORKER_LEASE_SECONDS", "0", "must be positive"),
        ("ETHNOWEAR_VISION_TIMEOUT_SECONDS", "0", "must be positive"),
        ("ETHNOWEAR_VISION_MAXIMUM_OUTPUT_BYTES", "0", "must be positive"),
        ("ETHNOWEAR_VISION_CONTEXT_TOKENS", "4096", "at least 8192"),
        ("ETHNOWEAR_VISION_MAXIMUM_GENERATED_TOKENS", "0", "must be positive"),
        ("ETHNOWEAR_VISION_MAXIMUM_IMAGE_EDGE_PIXELS", "1000", "at least 1024"),
        ("ETHNOWEAR_VISION_IMAGE_JPEG_QUALITY", "69", "between 70 and 100"),
        ("ETHNOWEAR_WORKER_TEMPORARY_ROOT", "tmp/vision", "absolute path"),
    ],
)
def test_rejects_invalid_values(
    monkeypatch: pytest.MonkeyPatch,
    name: str,
    value: str,
    message: str,
) -> None:
    configure_environment(monkeypatch)
    monkeypatch.setenv(name, value)

    with pytest.raises(ValueError, match=message):
        VisionWorkerSettings.from_environment()


@pytest.mark.parametrize(
    ("name", "value", "message"),
    [
        ("ETHNOWEAR_WORKER_LEASE_SECONDS", "later", "must be an integer"),
        ("ETHNOWEAR_VISION_MAXIMUM_OUTPUT_BYTES", "large", "must be an integer"),
        ("ETHNOWEAR_VISION_CONTEXT_TOKENS", "large", "must be an integer"),
        ("ETHNOWEAR_VISION_MAXIMUM_GENERATED_TOKENS", "large", "must be an integer"),
        ("ETHNOWEAR_VISION_MAXIMUM_IMAGE_EDGE_PIXELS", "large", "must be an integer"),
        ("ETHNOWEAR_VISION_IMAGE_JPEG_QUALITY", "large", "must be an integer"),
        ("ETHNOWEAR_WORKER_POLL_MIN_SECONDS", "soon", "must be a number"),
    ],
)
def test_rejects_non_numeric_values(
    monkeypatch: pytest.MonkeyPatch,
    name: str,
    value: str,
    message: str,
) -> None:
    configure_environment(monkeypatch)
    monkeypatch.setenv(name, value)

    with pytest.raises(ValueError, match=message):
        VisionWorkerSettings.from_environment()
