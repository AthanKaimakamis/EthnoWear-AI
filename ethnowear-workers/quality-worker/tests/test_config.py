from pathlib import Path

import pytest

from ethnowear_quality_worker.config import QualityWorkerSettings


def set_environment(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("ETHNOWEAR_WORKER_BASE_URL", "http://backend:8080")
    monkeypatch.setenv("ETHNOWEAR_WORKER_ID", "quality-worker-1")
    monkeypatch.setenv("ETHNOWEAR_WORKER_API_TOKEN", "x" * 32)


def test_settings_load_quality_worker_defaults(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    set_environment(monkeypatch)

    settings = QualityWorkerSettings.from_environment()

    assert settings.worker_id == "quality-worker-1"
    assert settings.lease_seconds == 120
    assert settings.temporary_root == Path("/tmp/ethnowear-quality-worker")


def test_settings_reject_relative_dictionary_path(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    set_environment(monkeypatch)
    monkeypatch.setenv("ETHNOWEAR_WORKER_BULGARIAN_DICTIONARY_PATH", "bg_BG.dic")

    with pytest.raises(ValueError, match="BULGARIAN_DICTIONARY_PATH"):
        QualityWorkerSettings.from_environment()
