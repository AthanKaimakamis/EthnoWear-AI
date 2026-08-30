from ethnowear_worker_common.health import is_ready

from ethnowear_vision_worker.config import VisionWorkerSettings


def main() -> int:
    try:
        settings = VisionWorkerSettings.from_environment()
    except ValueError:
        return 1

    return 0 if is_ready(settings.temporary_root) else 1


if __name__ == "__main__":
    raise SystemExit(main())
