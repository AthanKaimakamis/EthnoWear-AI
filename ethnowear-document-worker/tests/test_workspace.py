import stat
from pathlib import Path

import pytest

from ethnowear_worker.temp.workspace import JobWorkspace, cleanup_stale_workspaces


def test_workspace_creates_private_job_directory_and_removes_it(
    tmp_path: Path,
) -> None:
    with JobWorkspace.create(tmp_path, job_id=11, attempt=2) as workspace:
        job_directory = workspace.path

        assert job_directory.is_dir()
        assert job_directory.parent == tmp_path
        assert job_directory.name.startswith("job-11-attempt-2-")
        assert stat.S_IMODE(job_directory.stat().st_mode) == 0o700
        assert workspace.input_pdf == job_directory / "input.pdf"
        assert workspace.rendition_path(21, "jpg") == (
            job_directory / "page-21.jpg"
        )

        workspace.input_pdf.write_bytes(b"temporary")

    assert not job_directory.exists()


def test_workspace_cleans_up_when_processing_raises(tmp_path: Path) -> None:
    workspace_path: Path | None = None

    with pytest.raises(RuntimeError):
        with JobWorkspace.create(tmp_path, job_id=11, attempt=1) as workspace:
            workspace_path = workspace.path
            raise RuntimeError("processing failed")

    assert workspace_path is not None
    assert not workspace_path.exists()


@pytest.mark.parametrize(
    ("job_id", "attempt"),
    [(0, 1), (-1, 1), (1, 0), (1, -1)],
)
def test_workspace_rejects_invalid_job_identity(
    tmp_path: Path,
    job_id: int,
    attempt: int,
) -> None:
    with pytest.raises(ValueError):
        JobWorkspace.create(tmp_path, job_id, attempt)


@pytest.mark.parametrize("extension", ["../pdf", "/tmp/pdf", "jpg.exe", ""])
def test_workspace_rejects_unsafe_rendition_extensions(
    tmp_path: Path,
    extension: str,
) -> None:
    with JobWorkspace.create(tmp_path, job_id=11, attempt=1) as workspace:
        with pytest.raises(ValueError):
            workspace.rendition_path(21, extension)


def test_startup_cleanup_removes_only_worker_job_directories(tmp_path: Path) -> None:
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
