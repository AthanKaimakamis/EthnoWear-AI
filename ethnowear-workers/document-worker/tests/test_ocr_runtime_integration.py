import asyncio
import os
from pathlib import Path
import shutil
import subprocess

import pytest
from PIL import Image, ImageDraw, ImageFont

from ethnowear_document_worker.ocr.image import apply_rotation
from ethnowear_document_worker.ocr.tesseract import TesseractRunner


pytestmark = pytest.mark.integration

FONT_PATH = Path(
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
)


def require_ocr_runtime() -> None:
    if os.getenv("ETHNOWEAR_RUN_OCR_INTEGRATION") != "1":
        pytest.skip("native OCR integration tests are disabled")

    binary = shutil.which("tesseract")
    if binary is None or not FONT_PATH.is_file():
        pytest.fail("Tesseract or the integration-test font is unavailable")

    languages = subprocess.run(
        (binary, "--list-langs"),
        check=True,
        capture_output=True,
        text=True,
        timeout=10,
    ).stdout.splitlines()

    if not {"bul", "eng", "osd"}.issubset(languages):
        pytest.fail("Required Tesseract language data is unavailable")


def runner(language: str = "bul") -> TesseractRunner:
    return TesseractRunner(
        binary="tesseract",
        language=language,
        oem=1,
        psm=3,
        timeout_seconds=30,
    )


def save_text_image(
        path: Path,
        lines: list[str],
        *,
        foreground: int = 0,
        background: int = 255,
) -> None:
    image = Image.new("L", (1800, max(500, len(lines) * 70)), background)
    draw = ImageDraw.Draw(image)
    font = ImageFont.truetype(str(FONT_PATH), 48)

    for index, line in enumerate(lines):
        draw.text(
            (50, 30 + index * 65),
            line,
            font=font,
            fill=foreground,
        )

    image.save(path, "PNG")


def recognize(path: Path, language: str = "bul") -> str:
    output = asyncio.run(runner(language).recognize(path, 2_000_000))
    return " ".join(output.raw_text.split())


def test_native_ocr_reads_printed_bulgarian(tmp_path: Path) -> None:
    require_ocr_runtime()
    path = tmp_path / "bulgarian.png"
    save_text_image(path, ["Българска народна шевица"])

    assert "Българска народна шевица" in recognize(path)


def test_native_ocr_reads_mixed_bulgarian_and_latin(tmp_path: Path) -> None:
    require_ocr_runtime()
    path = tmp_path / "mixed.png"
    save_text_image(path, ["Българска шевица", "EthnoWear 2026"])

    text = recognize(path, "bul+eng")

    assert "Българска шевица" in text
    assert "EthnoWear 2026" in text


def test_native_ocr_corrects_rotated_dense_page(tmp_path: Path) -> None:
    require_ocr_runtime()
    source = tmp_path / "source.png"
    rotated = tmp_path / "rotated.png"
    corrected = tmp_path / "corrected.png"
    lines = [
        "Българската народна носия пази историята",
        "Шевицата съдържа геометрични орнаменти",
        "Традиционните техники се предават през поколения",
        "Цветовете и мотивите имат регионални различия",
    ] * 8
    save_text_image(source, lines)

    with Image.open(source) as image:
        image.rotate(90, expand=True, fillcolor="white").save(rotated, "PNG")

    tesseract = runner()
    rotation = asyncio.run(tesseract.detect_rotation(rotated))

    assert rotation == 90
    assert apply_rotation(rotated, corrected, rotation) is True
    assert "Българската народна носия" in recognize(corrected)


def test_native_ocr_reads_low_contrast_text(tmp_path: Path) -> None:
    require_ocr_runtime()
    path = tmp_path / "low-contrast.png"
    save_text_image(
        path,
        ["Българска шевица"],
        foreground=170,
        background=235,
    )

    assert "Българска шевица" in recognize(path)


def test_native_ocr_accepts_empty_page(tmp_path: Path) -> None:
    require_ocr_runtime()
    path = tmp_path / "empty.png"
    Image.new("L", (1200, 1200), "white").save(path, "PNG")

    output = asyncio.run(runner().recognize(path, 2_000_000))

    assert output.raw_text == ""
    assert output.words == ()
    assert output.mean_confidence is None
