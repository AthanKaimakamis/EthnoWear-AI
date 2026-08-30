from pathlib import Path

import pymupdf
import pytest

from ethnowear_document_worker.pdf.inspector import PdfPageInfo
from ethnowear_document_worker.pdf.renderer import PdfRenderError, render_page

from test_pdf_inspector import create_pdf


def test_renderer_writes_one_rgb_jpeg_at_exact_dpi(tmp_path: Path) -> None:
    pdf_path = tmp_path / "input.pdf"
    output_path = tmp_path / "page.jpg"
    create_pdf(pdf_path, [(72, 144)])
    page = PdfPageInfo(0, 1, 72, 144)

    result = render_page(
        pdf_path,
        page,
        output_path,
        render_dpi=72,
        maximum_pixel_width=1_000,
        maximum_pixel_height=1_000,
        maximum_page_pixels=1_000_000,
        maximum_bytes=1_000_000,
    )

    assert result.path == output_path
    assert result.mime_type == "image/jpeg"
    assert result.pixel_width == 72
    assert result.pixel_height == 144
    assert result.color_mode == "RGB"
    assert result.size_bytes == output_path.stat().st_size
    assert output_path.read_bytes().startswith(b"\xff\xd8")

    image = pymupdf.Pixmap(output_path)
    assert (image.width, image.height) == (72, 144)


def test_renderer_rejects_dimensions_before_rendering(tmp_path: Path) -> None:
    pdf_path = tmp_path / "input.pdf"
    create_pdf(pdf_path, [(72, 144)])

    with pytest.raises(PdfRenderError, match="pixel dimensions"):
        render_page(
            pdf_path,
            PdfPageInfo(0, 1, 72, 144),
            tmp_path / "page.jpg",
            72,
            50,
            1_000,
            50_000,
            1_000_000,
        )


def test_renderer_rejects_total_page_pixels_before_rendering(tmp_path: Path) -> None:
    pdf_path = tmp_path / "input.pdf"
    create_pdf(pdf_path, [(720, 720)])

    with pytest.raises(PdfRenderError, match="pixel dimensions"):
        render_page(
            pdf_path,
            PdfPageInfo(0, 1, 720, 720),
            tmp_path / "page.jpg",
            72,
            1_000,
            1_000,
            500_000,
            1_000_000,
        )


def test_renderer_removes_oversized_output(tmp_path: Path) -> None:
    pdf_path = tmp_path / "input.pdf"
    output_path = tmp_path / "page.jpg"
    create_pdf(pdf_path, [(72, 144)])

    with pytest.raises(PdfRenderError, match="maximum size"):
        render_page(
            pdf_path,
            PdfPageInfo(0, 1, 72, 144),
            output_path,
            72,
            1_000,
            1_000,
            1_000_000,
            1,
        )

    assert not output_path.exists()


def test_renderer_wraps_invalid_page_without_exposing_path(tmp_path: Path) -> None:
    pdf_path = tmp_path / "secret.pdf"
    create_pdf(pdf_path, [(72, 144)])

    with pytest.raises(PdfRenderError) as captured:
        render_page(
            pdf_path,
            PdfPageInfo(5, 6, 72, 144),
            tmp_path / "page.jpg",
            72,
            1_000,
            1_000,
            1_000_000,
            1_000_000,
        )

    assert str(pdf_path) not in str(captured.value)
