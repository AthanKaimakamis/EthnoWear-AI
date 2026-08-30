from pathlib import Path

import pytest

from ethnowear_worker_common.health import mark_ready
from ethnowear_vision_worker import health


def test_health_returns_ready_when_marker_exists(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("ETHNOWEAR_WORKER_BASE_URL", "http://backend:8080")
    monkeypatch.setenv("ETHNOWEAR_WORKER_ID", "vision-worker-1")
    monkeypatch.setenv("ETHNOWEAR_WORKER_API_TOKEN", "x" * 32)
    monkeypatch.setenv("ETHNOWEAR_OLLAMA_BASE_URL", "http://ollama:11434")
    monkeypatch.setenv("ETHNOWEAR_VISION_MODEL", "gemma3:4b")
    monkeypatch.setenv("ETHNOWEAR_VISION_PROMPT_VERSION", "vision-ocr-v1")
    monkeypatch.setenv("ETHNOWEAR_WORKER_TEMPORARY_ROOT", str(tmp_path))
    mark_ready(tmp_path)

    assert health.main() == 0


def test_health_returns_not_ready_without_marker(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("ETHNOWEAR_WORKER_BASE_URL", "http://backend:8080")
    monkeypatch.setenv("ETHNOWEAR_WORKER_ID", "vision-worker-1")
    monkeypatch.setenv("ETHNOWEAR_WORKER_API_TOKEN", "x" * 32)
    monkeypatch.setenv("ETHNOWEAR_OLLAMA_BASE_URL", "http://ollama:11434")
    monkeypatch.setenv("ETHNOWEAR_VISION_MODEL", "gemma3:4b")
    monkeypatch.setenv("ETHNOWEAR_VISION_PROMPT_VERSION", "vision-ocr-v1")
    monkeypatch.setenv("ETHNOWEAR_WORKER_TEMPORARY_ROOT", str(tmp_path))

    assert health.main() == 1


def test_health_returns_not_ready_for_invalid_configuration(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.delenv("ETHNOWEAR_WORKER_BASE_URL", raising=False)

    assert health.main() == 1
