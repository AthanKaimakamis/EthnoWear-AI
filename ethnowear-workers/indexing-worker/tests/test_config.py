import pytest

from ethnowear_indexer.config import IndexerSettings


REQUIRED_ENVIRONMENT = {
    "ETHNOWEAR_WORKER_BASE_URL": "http://localhost:8080/",
    "ETHNOWEAR_WORKER_ID": "indexing-worker-1",
    "ETHNOWEAR_WORKER_API_TOKEN": "x" * 32,
    "ETHNOWEAR_QDRANT_URL": "http://localhost:6333/",
    "ETHNOWEAR_QDRANT_COLLECTION": "ethnowear_chunks_bge_m3_v1",
}


def configure_environment(monkeypatch: pytest.MonkeyPatch) -> None:
    for name, value in REQUIRED_ENVIRONMENT.items():
        monkeypatch.setenv(name, value)


def test_from_env_loads_required_values_and_defaults(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    configure_environment(monkeypatch)

    settings = IndexerSettings.from_env()

    assert settings.api_base_url == "http://localhost:8080"
    assert settings.worker_id == "indexing-worker-1"
    assert settings.qdrant_url == "http://localhost:6333"
    assert settings.qdrant_collection == "ethnowear_chunks_bge_m3_v1"
    assert settings.poll_min_seconds == 1.0
    assert settings.poll_max_seconds == 30.0
    assert settings.lease_seconds == 120


def test_from_env_loads_numeric_overrides(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    configure_environment(monkeypatch)
    monkeypatch.setenv("ETHNOWEAR_WORKER_POLL_MIN_SECONDS", "2.5")
    monkeypatch.setenv("ETHNOWEAR_WORKER_POLL_MAX_SECONDS", "45")
    monkeypatch.setenv("ETHNOWEAR_WORKER_LEASE_SECONDS", "180")
    monkeypatch.setenv("ETHNOWEAR_EMBEDDING_TIMEOUT_SECONDS", "90")
    monkeypatch.setenv("ETHNOWEAR_QDRANT_TIMEOUT_SECONDS", "15")

    settings = IndexerSettings.from_env()

    assert settings.poll_min_seconds == 2.5
    assert settings.poll_max_seconds == 45.0
    assert settings.lease_seconds == 180
    assert settings.embedding_timeout_seconds == 90.0
    assert settings.qdrant_timeout_seconds == 15.0


@pytest.mark.parametrize("missing_name", REQUIRED_ENVIRONMENT)
def test_from_env_rejects_missing_required_value(
    monkeypatch: pytest.MonkeyPatch,
    missing_name: str,
) -> None:
    configure_environment(monkeypatch)
    monkeypatch.delenv(missing_name)

    with pytest.raises(ValueError, match=f"^{missing_name} is required$"):
        IndexerSettings.from_env()


@pytest.mark.parametrize(
    ("name", "value", "message"),
    [
        ("ETHNOWEAR_WORKER_BASE_URL", "localhost:8080", "valid HTTP or HTTPS URL"),
        ("ETHNOWEAR_QDRANT_URL", "not-a-url", "valid HTTP or HTTPS URL"),
        ("ETHNOWEAR_WORKER_API_TOKEN", "too-short", "at least 32 UTF-8 bytes"),
        ("ETHNOWEAR_WORKER_POLL_MIN_SECONDS", "0", "must be positive"),
        ("ETHNOWEAR_WORKER_POLL_MAX_SECONDS", "0.5", "greater than or equal"),
        ("ETHNOWEAR_WORKER_LEASE_SECONDS", "0", "must be positive"),
        ("ETHNOWEAR_EMBEDDING_TIMEOUT_SECONDS", "0", "must be positive"),
        ("ETHNOWEAR_QDRANT_TIMEOUT_SECONDS", "0", "must be positive"),
    ],
)
def test_from_env_rejects_invalid_configuration(
    monkeypatch: pytest.MonkeyPatch,
    name: str,
    value: str,
    message: str,
) -> None:
    configure_environment(monkeypatch)
    monkeypatch.setenv(name, value)

    with pytest.raises(ValueError, match=message):
        IndexerSettings.from_env()


@pytest.mark.parametrize(
    ("name", "value", "message"),
    [
        ("ETHNOWEAR_WORKER_LEASE_SECONDS", "later", "must be an integer"),
        ("ETHNOWEAR_WORKER_POLL_MIN_SECONDS", "soon", "must be a number"),
    ],
)
def test_from_env_rejects_non_numeric_configuration(
    monkeypatch: pytest.MonkeyPatch,
    name: str,
    value: str,
    message: str,
) -> None:
    configure_environment(monkeypatch)
    monkeypatch.setenv(name, value)

    with pytest.raises(ValueError, match=message):
        IndexerSettings.from_env()
