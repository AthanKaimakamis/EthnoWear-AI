import asyncio
import logging

from ethnowear_indexer.app import run_worker
from ethnowear_indexer.config import IndexerSettings
from ethnowear_indexer.logging import configure_logging


logger = logging.getLogger(__name__)


def main() -> int:
    configure_logging()

    try:
        settings = IndexerSettings.from_env()
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
