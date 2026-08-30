from unittest.mock import AsyncMock

from ethnowear_indexer import main as main_module


def test_main_returns_configuration_error(
        monkeypatch,
) -> None:
    monkeypatch.setattr(main_module, "configure_logging", lambda: None)
    monkeypatch.setattr(
        main_module.IndexerSettings,
        "from_env",
        lambda: (_ for _ in ()).throw(ValueError("secret")),
    )

    assert main_module.main() == 2


def test_main_runs_worker(
        monkeypatch,
) -> None:
    settings = object()
    run_worker = AsyncMock()
    monkeypatch.setattr(main_module, "configure_logging", lambda: None)
    monkeypatch.setattr(
        main_module.IndexerSettings,
        "from_env",
        lambda: settings,
    )
    monkeypatch.setattr(main_module, "run_worker", run_worker)

    assert main_module.main() == 0
    run_worker.assert_awaited_once_with(settings)


def test_main_returns_runtime_error_without_exposing_exception(
        monkeypatch,
        caplog,
) -> None:
    run_worker = AsyncMock(side_effect=RuntimeError("secret failure"))
    monkeypatch.setattr(main_module, "configure_logging", lambda: None)
    monkeypatch.setattr(
        main_module.IndexerSettings,
        "from_env",
        lambda: object(),
    )
    monkeypatch.setattr(main_module, "run_worker", run_worker)

    assert main_module.main() == 1
    assert "worker_stopped_unexpectedly" in caplog.text
    assert "secret failure" not in caplog.text
