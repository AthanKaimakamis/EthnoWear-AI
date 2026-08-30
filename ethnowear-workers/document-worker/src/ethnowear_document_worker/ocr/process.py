import asyncio
from collections.abc import Sequence


class OcrProcessError(RuntimeError):
    pass


class OcrProcessUnavailableError(OcrProcessError):
    pass


class OcrProcessTimeoutError(OcrProcessError):
    pass


class OcrProcessOutputLimitError(OcrProcessError):
    pass


async def run_bounded_process(
        command: Sequence[str],
        *,
        timeout_seconds: float,
        maximum_stdout_bytes: int,
        maximum_stderr_bytes: int = 8192,
) -> str:
    if not command:
        raise ValueError("OCR command is required")

    if timeout_seconds <= 0:
        raise ValueError("OCR process timeout must be positive")

    if maximum_stdout_bytes <= 0 or maximum_stderr_bytes <= 0:
        raise ValueError("OCR process output limits must be positive")

    try:
        process = await asyncio.create_subprocess_exec(
            *command,
            stdin=asyncio.subprocess.DEVNULL,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
    except (FileNotFoundError, PermissionError, OSError) as error:
        raise OcrProcessUnavailableError("OCR engine could not be started") from error

    assert process.stdout is not None
    assert process.stderr is not None

    stdout_task = asyncio.create_task(
        _read_bounded(process.stdout, maximum_stdout_bytes)
    )
    stderr_task = asyncio.create_task(
        _read_bounded(process.stderr, maximum_stderr_bytes)
    )

    try:
        stdout, _ = await asyncio.wait_for(
            asyncio.gather(
                stdout_task,
                stderr_task,
            ),
            timeout=timeout_seconds,
        )

        return_code = await process.wait()
    except TimeoutError as error:
        await _kill_process(process)

        await _cancel_tasks(stdout_task, stderr_task)
        raise OcrProcessTimeoutError("OCR processing exceeded its timeout") from error

    except OcrProcessOutputLimitError:
        await _kill_process(process)
        await _cancel_tasks(stdout_task, stderr_task)
        raise

    except asyncio.CancelledError:
        await _kill_process(process)
        await _cancel_tasks(stdout_task, stderr_task)
        raise

    except BaseException:
        await _kill_process(process)
        await _cancel_tasks(stdout_task, stderr_task)
        raise

    if return_code != 0:
        raise OcrProcessError("OCR engine processing failed")

    try:
        return stdout.decode("utf-8")
    except UnicodeDecodeError as error:
        raise OcrProcessError("OCR engine returned invalid text output") from error


async def _read_bounded(stream: asyncio.StreamReader, maximum_bytes: int) -> bytes:
    output = bytearray()

    while chunk := await stream.read(65_536):
        if len(output) + len(chunk) > maximum_bytes:
            raise OcrProcessOutputLimitError("OCR engine output exceeds the maximum size")

        output.extend(chunk)

    return bytes(output)


async def _kill_process(process: asyncio.subprocess.Process) -> None:
    if process.returncode is None:
        process.kill()

    await process.wait()


async def _cancel_tasks(*tasks: asyncio.Task[bytes]) -> None:
    for task in tasks:
        if not task.done():
            task.cancel()

    await asyncio.gather(*tasks, return_exceptions=True)
