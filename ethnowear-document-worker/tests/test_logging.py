import json
import logging

from ethnowear_worker.logging import JsonFormatter


def test_json_formatter_emits_safe_structured_fields() -> None:
    record = logging.LogRecord(
        name="ethnowear_worker.jobs.processor",
        level=logging.INFO,
        pathname=__file__,
        lineno=12,
        msg="job_completed",
        args=(),
        exc_info=None,
    )
    record.job_id = 11
    record.attempt = 2
    record.duration_ms = 125

    result = json.loads(JsonFormatter().format(record))

    assert result["level"] == "INFO"
    assert result["event"] == "job_completed"
    assert result["job_id"] == 11
    assert result["attempt"] == 2
    assert result["duration_ms"] == 125
    assert result["timestamp"].endswith("+00:00")


def test_json_formatter_excludes_unapproved_fields_and_stack_trace() -> None:
    secret = "token-and-/private/path"

    try:
        raise RuntimeError(secret)
    except RuntimeError:
        import sys

        exception_info = sys.exc_info()

    record = logging.LogRecord(
        name="ethnowear_worker.jobs.processor",
        level=logging.ERROR,
        pathname=__file__,
        lineno=35,
        msg="job_failed",
        args=(),
        exc_info=exception_info,
    )
    record.error_code = "WORKER_INTERNAL_ERROR"
    record.claim_token = secret
    record.temporary_path = "/private/path"

    serialized = JsonFormatter().format(record)
    result = json.loads(serialized)

    assert result["error_code"] == "WORKER_INTERNAL_ERROR"
    assert "claim_token" not in result
    assert "temporary_path" not in result
    assert secret not in serialized
    assert "Traceback" not in serialized
