from pathlib import Path

from ethnowear_worker.health import (
    is_ready,
    mark_not_ready,
    mark_ready,
    readiness_path,
)


def test_readiness_marker_has_private_permissions(tmp_path: Path) -> None:
    temporary_root = tmp_path / "worker"

    mark_ready(temporary_root)

    marker = readiness_path(temporary_root)
    assert is_ready(temporary_root) is True
    assert marker.read_text(encoding="utf-8") == "ready\n"
    assert marker.stat().st_mode & 0o777 == 0o600
    assert temporary_root.stat().st_mode & 0o077 == 0


def test_not_ready_removes_marker_idempotently(tmp_path: Path) -> None:
    temporary_root = tmp_path / "worker"
    mark_ready(temporary_root)

    mark_not_ready(temporary_root)
    mark_not_ready(temporary_root)

    assert is_ready(temporary_root) is False


def test_readiness_rejects_insecure_marker(tmp_path: Path) -> None:
    temporary_root = tmp_path / "worker"
    mark_ready(temporary_root)
    readiness_path(temporary_root).chmod(0o644)

    assert is_ready(temporary_root) is False
