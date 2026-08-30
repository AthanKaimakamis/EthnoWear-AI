import stat
from pathlib import Path

import pytest

from ethnowear_worker_common.temp.workspace import (
    JobWorkspace,
    cleanup_stale_workspaces,
)


def test_workspace_is_private_and_removed_on_exit(tmp_path: Path) -> None:
    with JobWorkspace.create(tmp_path, job_id=11, attempt=2) as workspace:
        path = workspace.path

        assert path.is_dir()
        assert path.name.startswith("job-11-attempt-2-")
        assert stat.S_IMODE(path.stat().st_mode) == 0o700
        assert workspace.file("input.png") == path / "input.png"

    assert not path.exists()


@pytest.mark.parametrize("name", ["", ".", "..", "../input", "/tmp/input"])
def test_workspace_rejects_unsafe_file_names(
    tmp_path: Path,
    name: str,
) -> None:
    with JobWorkspace.create(tmp_path, job_id=11, attempt=1) as workspace:
        with pytest.raises(ValueError):
            workspace.file(name)


def test_workspace_removes_files_when_processing_raises(tmp_path: Path) -> None:
    path: Path | None = None

    with pytest.raises(RuntimeError):
        with JobWorkspace.create(tmp_path, job_id=11, attempt=1) as workspace:
            path = workspace.path
            workspace.file("input.png").write_bytes(b"temporary")
            raise RuntimeError("processing failed")

    assert path is not None
    assert not path.exists()


def test_cleanup_removes_only_worker_directories(tmp_path: Path) -> None:
    stale = tmp_path / "job-11-attempt-1-abcd"
    stale.mkdir()
    unrelated = tmp_path / "keep-me"
    unrelated.mkdir()
    symlink = tmp_path / "job-12-attempt-1-link"
    symlink.symlink_to(unrelated, target_is_directory=True)

    assert cleanup_stale_workspaces(tmp_path) == 1
    assert not stale.exists()
    assert unrelated.exists()
    assert symlink.is_symlink()
