import asyncio
from pathlib import Path

import pytest

from ethnowear_document_worker.ocr.process import OcrProcessError
from ethnowear_document_worker.ocr.tesseract import TesseractRunner


HEADER = (
    "level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\t"
    "left\ttop\twidth\theight\tconf\ttext"
)


def runner() -> TesseractRunner:
    return TesseractRunner(
        binary="/usr/bin/tesseract",
        language="bul",
        oem=1,
        psm=3,
        timeout_seconds=120,
    )


def test_recognize_invokes_tesseract_and_returns_parsed_output(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    calls: list[tuple[tuple[str, ...], float, int]] = []
    tsv = "\n".join([
        HEADER,
        "5\t1\t1\t1\t1\t1\t10\t20\t30\t40\t92\tшевица",
    ])

    async def fake_process(
        command,
        *,
        timeout_seconds,
        maximum_stdout_bytes,
        maximum_stderr_bytes=8192,
    ) -> str:
        calls.append((tuple(command), timeout_seconds, maximum_stdout_bytes))
        if command[-1] == "--version":
            return "tesseract 5.5.0\n leptonica-1.84"
        return tsv if command[-1] == "tsv" else "шевица\n"

    monkeypatch.setattr(
        "ethnowear_document_worker.ocr.tesseract.run_bounded_process",
        fake_process,
    )

    result = asyncio.run(
        runner().recognize(Path("/temporary/page.png"), 50_000)
    )

    assert calls[0] == ((
        "/usr/bin/tesseract",
        "/temporary/page.png",
        "stdout",
        "-l",
        "bul",
        "--oem",
        "1",
        "--psm",
        "3",
        "txt",
    ), 120, 50_000)
    assert calls[1][0][-1] == "tsv"
    assert result.raw_text == "шевица"
    assert result.mean_confidence == pytest.approx(0.92)
    assert result.engine_name == "tesseract"
    assert result.engine_version == "5.5.0"
    assert result.language == "bul"


def test_recognize_rejects_tsv_rows_as_plain_text(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    async def fake_process(command, **kwargs) -> str:
        return HEADER if command[-1] == "txt" else HEADER

    monkeypatch.setattr(
        "ethnowear_document_worker.ocr.tesseract.run_bounded_process",
        fake_process,
    )

    with pytest.raises(OcrProcessError, match="structured rows"):
        asyncio.run(runner().recognize(Path("/temporary/page.png"), 50_000))


def test_engine_version_is_cached(monkeypatch: pytest.MonkeyPatch) -> None:
    calls = 0

    async def fake_process(*args, **kwargs) -> str:
        nonlocal calls
        calls += 1
        return "tesseract 5.5.0\n"

    monkeypatch.setattr(
        "ethnowear_document_worker.ocr.tesseract.run_bounded_process",
        fake_process,
    )
    tesseract = runner()

    assert asyncio.run(tesseract.engine_version()) == "5.5.0"
    assert asyncio.run(tesseract.engine_version()) == "5.5.0"
    assert calls == 1


def test_detect_rotation_uses_runner_configuration(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    captured: dict[str, object] = {}

    async def fake_detect(path: Path, **kwargs) -> int:
        captured["path"] = path
        captured.update(kwargs)
        return 90

    monkeypatch.setattr(
        "ethnowear_document_worker.ocr.tesseract.detect_rotation",
        fake_detect,
    )
    image_path = Path("/temporary/page.png")

    assert asyncio.run(runner().detect_rotation(image_path)) == 90
    assert captured == {
        "path": image_path,
        "binary": "/usr/bin/tesseract",
        "timeout_seconds": 120,
    }


def test_validate_runtime_accepts_configured_languages(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    outputs = iter((
        "tesseract 5.5.0\n",
        "List of available languages (3):\nbul\neng\nosd\n",
    ))

    async def fake_process(*args, **kwargs) -> str:
        return next(outputs)

    monkeypatch.setattr(
        "ethnowear_document_worker.ocr.tesseract.run_bounded_process",
        fake_process,
    )

    assert asyncio.run(runner().validate_runtime()) == "5.5.0"


@pytest.mark.parametrize(
    "available_languages",
    [
        "bul\neng\n",
        "eng\nosd\n",
    ],
)
def test_validate_runtime_rejects_missing_language_data(
    monkeypatch: pytest.MonkeyPatch,
    available_languages: str,
) -> None:
    outputs = iter((
        "tesseract 5.5.0\n",
        "List of available languages:\n" + available_languages,
    ))

    async def fake_process(*args, **kwargs) -> str:
        return next(outputs)

    monkeypatch.setattr(
        "ethnowear_document_worker.ocr.tesseract.run_bounded_process",
        fake_process,
    )

    with pytest.raises(OcrProcessError, match="language data"):
        asyncio.run(runner().validate_runtime())


@pytest.mark.parametrize(
    "output",
    [
        "",
        "unexpected-version",
        "other-engine 5.5.0",
        "tesseract " + "1" * 101,
    ],
)
def test_engine_version_rejects_invalid_output(
    monkeypatch: pytest.MonkeyPatch,
    output: str,
) -> None:
    async def fake_process(*args, **kwargs) -> str:
        return output

    monkeypatch.setattr(
        "ethnowear_document_worker.ocr.tesseract.run_bounded_process",
        fake_process,
    )

    with pytest.raises(OcrProcessError, match="invalid version"):
        asyncio.run(runner().engine_version())
