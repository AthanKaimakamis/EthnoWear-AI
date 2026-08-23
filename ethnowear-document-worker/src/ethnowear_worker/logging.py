import json
import logging
from datetime import datetime, UTC

SAFE_FIELDS = {
    "worker_id",
    "job_id",
    "attempt",
    "error_code",
    "retryable",
    "duration_ms"
}


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        entry = {
            "timestamp": datetime.now(UTC).isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "event": record.getMessage(),
        }

        for field in SAFE_FIELDS:
            value = getattr(record, field, None)

            if value is not None:
                entry[field] = value

        return json.dumps(
            entry,
            separators=(",", ":"),
            ensure_ascii=False,
        )


def configure_logging(level: int = logging.INFO) -> None:
    handler = logging.StreamHandler()
    handler.setFormatter(JsonFormatter())

    root_logger = logging.getLogger()
    root_logger.handlers.clear()
    root_logger.addHandler(handler)
    root_logger.setLevel(level)
    logging.getLogger("httpx").setLevel(logging.WARNING)
