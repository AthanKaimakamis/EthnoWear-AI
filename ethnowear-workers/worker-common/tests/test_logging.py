import json
import logging

from ethnowear_worker_common.logging import JsonFormatter


def test_formatter_keeps_diagnostics_and_drops_document_text() -> None:
    record = logging.LogRecord(
        name="vision",
        level=logging.INFO,
        pathname=__file__,
        lineno=10,
        msg="vision_model_call_completed",
        args=(),
        exc_info=None,
    )
    record.job_id = 100854
    record.validation_stage = "number_grounding"
    record.prompt_eval_count = 2032
    record.eval_count = 879
    record.done_reason = "stop"
    record.raw_ocr_text = "private document text"
    record.raw_response = "private model response"

    output = json.loads(JsonFormatter().format(record))

    assert output["job_id"] == 100854
    assert output["validation_stage"] == "number_grounding"
    assert output["prompt_eval_count"] == 2032
    assert output["eval_count"] == 879
    assert output["done_reason"] == "stop"
    assert "raw_ocr_text" not in output
    assert "raw_response" not in output
    assert "private" not in json.dumps(output)
