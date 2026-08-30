import asyncio
import logging

from ethnowear_worker_common.logging import configure_logging

from ethnowear_quality_worker.app import run_worker
from ethnowear_quality_worker.config import QualityWorkerSettings


logger = logging.getLogger(__name__)


def main() -> int:
    configure_logging()

    try:
        settings = QualityWorkerSettings.from_environment()
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