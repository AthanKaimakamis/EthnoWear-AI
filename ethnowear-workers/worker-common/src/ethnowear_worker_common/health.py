from pathlib import Path


READINESS_FILENAME = "worker-ready"


def readiness_path(temporary_root: Path) -> Path:
    return temporary_root / READINESS_FILENAME


def mark_ready(temporary_root: Path) -> None:
    temporary_root.mkdir(
        mode=0o700,
        parents=True,
        exist_ok=True,
    )

    path = readiness_path(temporary_root)
    path.write_text("ready\n", encoding="utf-8")
    path.chmod(0o600)


def mark_not_ready(temporary_root: Path) -> None:
    readiness_path(temporary_root).unlink(missing_ok=True)


def is_ready(temporary_root: Path) -> bool:
    path = readiness_path(temporary_root)

    return (
        path.is_file()
        and not path.is_symlink()
        and path.stat().st_mode & 0o077 == 0
    )
