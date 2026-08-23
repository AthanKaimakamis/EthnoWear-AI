from pathlib import Path

import pymupdf
import pytest

from ethnowear_worker.pdf.inspector import PdfInspectionError, inspect_pdf


def create_pdf(path: Path, sizes: list[tuple[float, float]]) -> None:
    document = pymupdf.open()
    try:
        for width, height in sizes:
            document.new_page(width=width, height=height)
        document.save(path)
    finally:
        document.close()


def test_inspector_returns_page_count_and_predicted_pixels(tmp_path: Path) -> None:
    pdf_path = tmp_path / "input.pdf"
    create_pdf(pdf_path, [(72, 144), (144, 72)])

    result = inspect_pdf(
        pdf_path,
        maximum_page_count=10,
        render_dpi=300,
        maximum_pixel_width=1_000,
        maximum_pixel_height=1_000,
        maximum_page_pixels=1_000_000,
    )

    assert result.page_count == 2
    assert result.pages[0].pdf_page_index == 0
    assert result.pages[0].page_sequence == 1
    assert result.pages[0].pixel_width == 300
    assert result.pages[0].pixel_height == 600
    assert result.pages[1].page_sequence == 2


def test_inspector_rejects_page_count_over_limit(tmp_path: Path) -> None:
    pdf_path = tmp_path / "input.pdf"
    create_pdf(pdf_path, [(72, 72), (72, 72)])

    with pytest.raises(PdfInspectionError, match="page count"):
        inspect_pdf(pdf_path, 1, 300, 1_000, 1_000, 1_000_000)


def test_inspector_rejects_dimensions_before_rendering(tmp_path: Path) -> None:
    pdf_path = tmp_path / "input.pdf"
    create_pdf(pdf_path, [(1_000, 1_000)])

    with pytest.raises(PdfInspectionError, match="pixel dimensions"):
        inspect_pdf(pdf_path, 10, 300, 1_000, 1_000, 1_000_000)


def test_inspector_rejects_total_page_pixels_before_rendering(tmp_path: Path) -> None:
    pdf_path = tmp_path / "input.pdf"
    create_pdf(pdf_path, [(720, 720)])

    with pytest.raises(PdfInspectionError, match="pixel count"):
        inspect_pdf(pdf_path, 10, 72, 1_000, 1_000, 500_000)


def test_inspector_wraps_invalid_pdf_without_exposing_path(tmp_path: Path) -> None:
    pdf_path = tmp_path / "secret-input.pdf"
    pdf_path.write_bytes(b"%PDF-invalid")

    with pytest.raises(PdfInspectionError) as captured:
        inspect_pdf(pdf_path, 10, 300, 1_000, 1_000, 1_000_000)

    assert str(pdf_path) not in str(captured.value)
