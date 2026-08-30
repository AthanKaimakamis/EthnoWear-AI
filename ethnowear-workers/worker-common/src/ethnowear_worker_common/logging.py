import json
import logging
from datetime import UTC, datetime


SAFE_FIELDS = {
    "worker_id",
    "job_id",
    "job_type",
    "attempt",
    "error_code",
    "retryable",
    "duration_ms",
    "document_page_id",
    "ocr_result_id",
    "model",
    "prompt_characters",
    "ocr_characters",
    "image_width",
    "image_height",
    "image_bytes",
    "response_bytes",
    "done_reason",
    "total_duration_ns",
    "load_duration_ns",
    "prompt_eval_count",
    "prompt_eval_duration_ns",
    "eval_count",
    "eval_duration_ns",
    "validation_stage",
    "processing_stage",
    "exception_type",
    "mapping_validation_code",
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

        return json.dumps(entry, separators=(",", ":"), ensure_ascii=False)


def configure_logging(level: int = logging.INFO) -> None:
    handler = logging.StreamHandler()
    handler.setFormatter(JsonFormatter())

    root_logger = logging.getLogger()
    root_logger.handlers.clear()
    root_logger.addHandler(handler)
    root_logger.setLevel(level)
    logging.getLogger("httpx").setLevel(logging.WARNING)
