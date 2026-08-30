from ethnowear_indexer.config import IndexerSettings
from ethnowear_worker_common.health import (
    is_ready,
    mark_not_ready,
    mark_ready,
    readiness_path
)


def main() -> int:
    try:
        settings = IndexerSettings.from_env()
    except ValueError:
        return 1

    return 0 if is_ready(settings.temporary_root) else 1


if __name__ == "__main__":
    raise SystemExit(main())
