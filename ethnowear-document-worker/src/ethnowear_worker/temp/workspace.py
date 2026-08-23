import shutil
import tempfile
from dataclasses import dataclass
from pathlib import Path
from types import TracebackType

_WORKSPACE_PREFIX = "job-"


def cleanup_stale_workspaces(temporary_root: Path) -> int:
    if not temporary_root.exists():
        return 0

    removed = 0
    for entry in temporary_root.iterdir():
        if (not entry.name.startswith(_WORKSPACE_PREFIX)
                or "-attempt-" not in entry.name
                or entry.is_symlink()
                or not entry.is_dir()):
            continue
        shutil.rmtree(entry)
        removed += 1
    return removed


@dataclass(frozen=True, slots=True)
class JobWorkspace:
    path: Path

    @classmethod
    def create(
            cls,
            temporary_root: Path,
            job_id: int,
            attempt: int
    ) -> "JobWorkspace":
        if job_id <= 0:
            raise ValueError("Job ID must be positive")

        if attempt <= 0:
            raise ValueError("Attempt must be positive")

        temporary_root.mkdir(
            mode=0o700,
            parents=True,
            exist_ok=True,
        )

        path = Path(
            tempfile.mkdtemp(
                prefix=f"job-{job_id}-attempt-{attempt}-",
                dir=temporary_root,
            )
        )
        path.chmod(0o700)

        return cls(path=path)

    @property
    def input_pdf(self) -> Path:
        return self.path / "input.pdf"

    def rendition_path(
            self,
            page_id: int,
            extension: str
    ) -> Path:
       if page_id <= 0:
           raise ValueError("Page ID must be positive")

       if extension not in {"jpg", "png", "webp"}:
           raise ValueError("Unsupported rendition extension")

       return self.path / f"page-{page_id}.{extension}"

    def __enter__(self) -> "JobWorkspace":
        return self

    def __exit__(
            self,
            exception_type: type[BaseException] | None,
            exception: BaseException | None,
            traceback: TracebackType | None,
    ) -> None:
        shutil.rmtree(self.path, ignore_errors=True)
