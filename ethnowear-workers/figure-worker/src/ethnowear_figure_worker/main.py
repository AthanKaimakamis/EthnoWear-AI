import asyncio
import logging

from ethnowear_worker_common.logging import configure_logging

from ethnowear_figure_worker.app import run_worker
from ethnowear_figure_worker.config import FigureWorkerSettings

logger = logging.getLogger(__name__)


def main() -> int:
    configure_logging()

    try:
        settings = FigureWorkerSettings.from_environment()
    except ValueError:
        logger.error("worker_configuration_invalid")
        return 2

    try:
        asyncio.run(run_worker(settings))
        return 0
    except KeyboardInterrupt:
        return 0
    except Exception:
        logger.error("worker_stopped_unexpectedly")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
