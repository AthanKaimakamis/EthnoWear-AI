import asyncio
import sys

import pytest

from ethnowear_document_worker.ocr.process import (
    OcrProcessError,
    OcrProcessOutputLimitError,
    OcrProcessTimeoutError,
    OcrProcessUnavailableError,
    run_bounded_process,
)


def run_python(
    script: str,
    *,
    timeout_seconds: float = 2,
    maximum_stdout_bytes: int = 1024,
    maximum_stderr_bytes: int = 1024,
) -> str:
    return asyncio.run(run_bounded_process(
        (sys.executable, "-c", script),
        timeout_seconds=timeout_seconds,
        maximum_stdout_bytes=maximum_stdout_bytes,
        maximum_stderr_bytes=maximum_stderr_bytes,
    ))


def test_run_bounded_process_returns_utf8_stdout_and_drains_stderr() -> None:
    result = run_python(
        "import sys; "
        "sys.stdout.write('Български'); "
        "sys.stderr.write('diagnostic')"
    )

    assert result == "Български"


def test_run_bounded_process_hides_stderr_on_failure() -> None:
    secret = "/private/tmp/secret-page.png"

    with pytest.raises(OcrProcessError) as captured:
        run_python(
            f"import sys; sys.stderr.write('{secret}'); sys.exit(2)"
        )

    assert str(captured.value) == "OCR engine processing failed"
    assert secret not in str(captured.value)


@pytest.mark.parametrize(
    ("stream", "stdout_limit", "stderr_limit"),
    [
        ("stdout", 4, 1024),
        ("stderr", 1024, 4),
    ],
)
def test_run_bounded_process_enforces_output_limits(
    stream: str,
    stdout_limit: int,
    stderr_limit: int,
) -> None:
    with pytest.raises(OcrProcessOutputLimitError, match="maximum size"):
        run_python(
            f"import sys; sys.{stream}.write('12345')",
            maximum_stdout_bytes=stdout_limit,
            maximum_stderr_bytes=stderr_limit,
        )


def test_run_bounded_process_enforces_timeout() -> None:
    with pytest.raises(OcrProcessTimeoutError, match="timeout"):
        run_python(
            "import time; time.sleep(5)",
            timeout_seconds=0.05,
        )


def test_run_bounded_process_propagates_cancellation() -> None:
    async def execute() -> None:
        task = asyncio.create_task(run_bounded_process(
            (sys.executable, "-c", "import time; time.sleep(5)"),
            timeout_seconds=10,
            maximum_stdout_bytes=100,
        ))
        await asyncio.sleep(0.05)
        task.cancel()

        with pytest.raises(asyncio.CancelledError):
            await task

    asyncio.run(execute())


def test_run_bounded_process_rejects_invalid_utf8() -> None:
    with pytest.raises(OcrProcessError, match="invalid text output"):
        run_python("import os; os.write(1, b'\\xff')")


def test_run_bounded_process_maps_unavailable_binary() -> None:
    async def execute() -> None:
        await run_bounded_process(
            ("/definitely/not/a/real/tesseract", "--version"),
            timeout_seconds=1,
            maximum_stdout_bytes=100,
        )

    with pytest.raises(OcrProcessUnavailableError, match="could not be started"):
        asyncio.run(execute())


@pytest.mark.parametrize(
    "kwargs",
    [
        {"command": (), "timeout_seconds": 1, "maximum_stdout_bytes": 1},
        {"command": ("x",), "timeout_seconds": 0, "maximum_stdout_bytes": 1},
        {"command": ("x",), "timeout_seconds": 1, "maximum_stdout_bytes": 0},
    ],
)
def test_run_bounded_process_rejects_invalid_configuration(kwargs) -> None:
    with pytest.raises(ValueError):
        asyncio.run(run_bounded_process(**kwargs))
