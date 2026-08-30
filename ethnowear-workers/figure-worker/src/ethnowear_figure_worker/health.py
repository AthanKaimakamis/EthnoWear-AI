from ethnowear_worker_common.health import is_ready

from ethnowear_figure_worker.config import FigureWorkerSettings


def main() -> int:
    try:
        settings = FigureWorkerSettings.from_environment()
    except ValueError:
        return 1

    return (
        0
        if is_ready(settings.temporary_root)
        else 1
    )


if __name__ == "__main__":
    raise SystemExit(main())
