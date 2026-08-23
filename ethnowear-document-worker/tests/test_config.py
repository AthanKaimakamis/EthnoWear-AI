import pytest
from pathlib import Path

from ethnowear_worker.config import WorkerSettings


def set_required_environment(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("ETHNOWEAR_WORKER_BASE_URL", "http://backend:8080/")
    monkeypatch.setenv("ETHNOWEAR_WORKER_ID", "page-extractor-1")
    monkeypatch.setenv("ETHNOWEAR_WORKER_API_TOKEN", "x" * 32)


def test_loads_valid_settings_and_applies_poll_defaults(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    set_required_environment(monkeypatch)
    monkeypatch.delenv("ETHNOWEAR_WORKER_POLL_MIN_SECONDS", raising=False)
    monkeypatch.delenv("ETHNOWEAR_WORKER_POLL_MAX_SECONDS", raising=False)
    monkeypatch.delenv("ETHNOWEAR_WORKER_TEMPORARY_ROOT", raising=False)
    monkeypatch.delenv(
        "ETHNOWEAR_WORKER_HTTP_CONNECT_TIMEOUT_SECONDS", raising=False
    )
    monkeypatch.delenv(
        "ETHNOWEAR_WORKER_HTTP_READ_TIMEOUT_SECONDS", raising=False
    )

    settings = WorkerSettings.from_environment()

    assert settings.base_url == "http://backend:8080"
    assert settings.worker_id == "page-extractor-1"
    assert settings.api_token == "x" * 32
    assert settings.poll_min_seconds == 1.0
    assert settings.poll_max_seconds == 30.0
    assert settings.temporary_root == Path("/tmp/ethnowear-worker")
    assert settings.http_connect_timeout_seconds == 10.0
    assert settings.http_read_timeout_seconds == 60.0


@pytest.mark.parametrize(
    ("missing_name", "expected_message"),
    [
        ("ETHNOWEAR_WORKER_BASE_URL", "ETHNOWEAR_WORKER_BASE_URL is required"),
        ("ETHNOWEAR_WORKER_ID", "ETHNOWEAR_WORKER_ID is required"),
        ("ETHNOWEAR_WORKER_API_TOKEN", "ETHNOWEAR_WORKER_API_TOKEN is required"),
    ],
)
def test_rejects_missing_required_settings(
    monkeypatch: pytest.MonkeyPatch,
    missing_name: str,
    expected_message: str,
) -> None:
    set_required_environment(monkeypatch)
    monkeypatch.delenv(missing_name)

    with pytest.raises(ValueError, match=expected_message):
        WorkerSettings.from_environment()


def test_rejects_invalid_base_url(monkeypatch: pytest.MonkeyPatch) -> None:
    set_required_environment(monkeypatch)
    monkeypatch.setenv("ETHNOWEAR_WORKER_BASE_URL", "backend:8080")

    with pytest.raises(ValueError, match="valid HTTP or HTTPS URL"):
        WorkerSettings.from_environment()


def test_rejects_short_api_token(monkeypatch: pytest.MonkeyPatch) -> None:
    set_required_environment(monkeypatch)
    monkeypatch.setenv("ETHNOWEAR_WORKER_API_TOKEN", "too-short")

    with pytest.raises(ValueError, match="at least 32 UTF-8 bytes"):
        WorkerSettings.from_environment()


def test_rejects_non_numeric_poll_interval(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    set_required_environment(monkeypatch)
    monkeypatch.setenv("ETHNOWEAR_WORKER_POLL_MIN_SECONDS", "soon")

    with pytest.raises(ValueError, match="must be a number"):
        WorkerSettings.from_environment()


def test_rejects_poll_minimum_greater_than_maximum(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    set_required_environment(monkeypatch)
    monkeypatch.setenv("ETHNOWEAR_WORKER_POLL_MIN_SECONDS", "31")
    monkeypatch.setenv("ETHNOWEAR_WORKER_POLL_MAX_SECONDS", "30")

    with pytest.raises(ValueError, match="greater than or equal"):
        WorkerSettings.from_environment()


def test_loads_runtime_setting_overrides(monkeypatch: pytest.MonkeyPatch) -> None:
    set_required_environment(monkeypatch)
    monkeypatch.setenv("ETHNOWEAR_WORKER_TEMPORARY_ROOT", "/var/tmp/worker")
    monkeypatch.setenv("ETHNOWEAR_WORKER_HTTP_CONNECT_TIMEOUT_SECONDS", "4.5")
    monkeypatch.setenv("ETHNOWEAR_WORKER_HTTP_READ_TIMEOUT_SECONDS", "90")

    settings = WorkerSettings.from_environment()

    assert settings.temporary_root == Path("/var/tmp/worker")
    assert settings.http_connect_timeout_seconds == 4.5
    assert settings.http_read_timeout_seconds == 90.0


def test_rejects_relative_temporary_root(monkeypatch: pytest.MonkeyPatch) -> None:
    set_required_environment(monkeypatch)
    monkeypatch.setenv("ETHNOWEAR_WORKER_TEMPORARY_ROOT", "tmp/worker")

    with pytest.raises(ValueError, match="must be an absolute path"):
        WorkerSettings.from_environment()


@pytest.mark.parametrize(
    "name",
    [
        "ETHNOWEAR_WORKER_HTTP_CONNECT_TIMEOUT_SECONDS",
        "ETHNOWEAR_WORKER_HTTP_READ_TIMEOUT_SECONDS",
    ],
)
def test_rejects_non_positive_http_timeout(
    monkeypatch: pytest.MonkeyPatch,
    name: str,
) -> None:
    set_required_environment(monkeypatch)
    monkeypatch.setenv(name, "0")

    with pytest.raises(ValueError, match="must be positive"):
        WorkerSettings.from_environment()
