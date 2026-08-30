import logging
import asyncio
from pathlib import Path

import pytest
import httpx

from ethnowear_document_worker import app
from ethnowear_document_worker import main as main_module
from ethnowear_document_worker.config import WorkerSettings
from ethnowear_document_worker.health import is_ready


def set_valid_environment(monkeypatch: pytest.MonkeyPatch) -> str:
    api_token = "secret-worker-token-that-is-long-enough"
    monkeypatch.setenv("ETHNOWEAR_WORKER_BASE_URL", "http://backend:8080")
    monkeypatch.setenv("ETHNOWEAR_WORKER_ID", "page-extractor-1")
    monkeypatch.setenv("ETHNOWEAR_WORKER_API_TOKEN", api_token)
    monkeypatch.delenv("ETHNOWEAR_WORKER_POLL_MIN_SECONDS", raising=False)
    monkeypatch.delenv("ETHNOWEAR_WORKER_POLL_MAX_SECONDS", raising=False)
    return api_token


def test_main_loads_settings_without_logging_the_token(
    monkeypatch: pytest.MonkeyPatch,
    caplog: pytest.LogCaptureFixture,
) -> None:
    api_token = set_valid_environment(monkeypatch)
    received_settings = []

    async def fake_run_worker(settings) -> None:
        received_settings.append(settings)

    monkeypatch.setattr(main_module, "configure_logging", lambda: None)
    monkeypatch.setattr(main_module, "run_worker", fake_run_worker)
    caplog.set_level(logging.INFO)

    exit_code = main_module.main()

    assert exit_code == 0
    assert len(received_settings) == 1
    assert received_settings[0].worker_id == "page-extractor-1"
    assert received_settings[0].base_url == "http://backend:8080"
    assert api_token not in caplog.text


def test_main_returns_configuration_error_without_a_traceback(
    monkeypatch: pytest.MonkeyPatch,
    caplog: pytest.LogCaptureFixture,
) -> None:
    api_token = set_valid_environment(monkeypatch)
    monkeypatch.delenv("ETHNOWEAR_WORKER_BASE_URL")
    monkeypatch.setattr(main_module, "configure_logging", lambda: None)
    caplog.set_level(logging.ERROR)

    exit_code = main_module.main()

    assert exit_code == 2
    assert "worker_configuration_invalid" in caplog.text
    assert "ETHNOWEAR_WORKER_BASE_URL is required" not in caplog.text
    assert api_token not in caplog.text


def test_main_sanitizes_unexpected_worker_failure(
    monkeypatch: pytest.MonkeyPatch,
    caplog: pytest.LogCaptureFixture,
) -> None:
    secret = set_valid_environment(monkeypatch)

    async def fail(_settings) -> None:
        raise RuntimeError(f"internal path and {secret}")

    monkeypatch.setattr(main_module, "configure_logging", lambda: None)
    monkeypatch.setattr(main_module, "run_worker", fail)
    caplog.set_level(logging.ERROR)

    assert main_module.main() == 1
    assert "worker_stopped_unexpectedly" in caplog.text
    assert secret not in caplog.text
    assert "internal path" not in caplog.text


def test_run_worker_marks_ready_only_while_runner_is_active(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    observed_readiness: list[bool] = []

    class FakeAsyncClient:
        def __init__(self, **kwargs) -> None:
            pass

        async def __aenter__(self):
            return self

        async def __aexit__(self, *args) -> None:
            pass

    class FakeRunner:
        def __init__(self, **kwargs) -> None:
            pass

        async def run(self, stop_event) -> None:
            observed_readiness.append(is_ready(tmp_path))

    class FakeApiClient:
        def __init__(self, *args, **kwargs) -> None:
            pass

        async def health(self) -> None:
            observed_readiness.append(is_ready(tmp_path))

    class FakeTesseractRunner:
        def __init__(self, **kwargs) -> None:
            pass

        async def validate_runtime(self) -> str:
            observed_readiness.append(is_ready(tmp_path))
            return "5.5.0"

    monkeypatch.setattr(app, "install_signal_handlers", lambda event: None)
    monkeypatch.setattr(app.httpx, "AsyncClient", FakeAsyncClient)
    monkeypatch.setattr(app, "WorkerApiClient", FakeApiClient)
    monkeypatch.setattr(app, "WorkerRunner", FakeRunner)
    monkeypatch.setattr(app, "TesseractRunner", FakeTesseractRunner)

    settings = WorkerSettings(
        base_url="http://backend:8080",
        worker_id="page-extractor-1",
        api_token="x" * 32,
        poll_min_seconds=1,
        poll_max_seconds=30,
        temporary_root=tmp_path,
    )

    import asyncio

    asyncio.run(app.run_worker(settings))

    assert observed_readiness == [False, False, True]
    assert is_ready(tmp_path) is False


def test_readiness_retries_api_outage_without_process_restart(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class RecoveringApiClient:
        calls = 0

        async def health(self) -> None:
            self.calls += 1
            if self.calls < 3:
                raise httpx.ConnectError("backend unavailable")

    delays: list[float] = []

    async def immediate_wait(_event, delay: float) -> bool:
        delays.append(delay)
        return False

    monkeypatch.setattr(app, "wait_for_stop", immediate_wait)
    monkeypatch.setattr(app, "bounded_jitter", lambda delay: delay)
    client = RecoveringApiClient()

    result = asyncio.run(app.wait_for_api_readiness(
        client, asyncio.Event(), minimum_delay=1, maximum_delay=30
    ))

    assert result is True
    assert client.calls == 3
    assert delays == [1, 2]
