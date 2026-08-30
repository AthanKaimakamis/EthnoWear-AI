from dataclasses import dataclass
from pathlib import Path

import pymupdf

from ethnowear_document_worker.pdf.inspector import PdfPageInfo


class PdfRenderError(RuntimeError):
    pass


@dataclass(frozen=True, slots=True)
class RenderedPage:
    path: Path
    mime_type: str
    size_bytes: int
    pixel_width: int
    pixel_height: int
    color_mode: str


def render_page(
        pdf_path: Path,
        page_info: PdfPageInfo,
        output_path: Path,
        render_dpi: int,
        maximum_pixel_width: int,
        maximum_pixel_height: int,
        maximum_page_pixels: int,
        maximum_bytes: int,
) -> RenderedPage:
    if min(
            render_dpi,
            maximum_pixel_width,
            maximum_pixel_height,
            maximum_page_pixels,
            maximum_bytes,
    ) <= 0:
        raise ValueError("Rendering limit must be positive")

    if (
            page_info.pixel_width > maximum_pixel_width
            or page_info.pixel_height > maximum_pixel_height
            or page_info.pixel_width * page_info.pixel_height > maximum_page_pixels
    ):
        raise PdfRenderError("PDF page pixel dimensions exceed the maximum")

    try:
        document = pymupdf.open(pdf_path)

        try:
            page = document.load_page(page_info.pdf_page_index)
            pixmap = page.get_pixmap(
                dpi=render_dpi,
                colorspace=pymupdf.csRGB,
                alpha=False,
                annots=True,
            )

            if (pixmap.width > maximum_pixel_width
                    or pixmap.height > maximum_pixel_height
                    or pixmap.width * pixmap.height > maximum_page_pixels):
                raise PdfRenderError("Rendered pixel dimensions exceed the maximum")

            if pixmap.width != page_info.pixel_width or pixmap.height != page_info.pixel_height:
                raise PdfRenderError("Rendered dimensions differ from inspection")

            pixmap.save(output_path, jpg_quality=92)
        finally:
            document.close()

        size_bytes = output_path.stat().st_size

        if size_bytes > maximum_bytes:
            raise PdfRenderError("Rendered page exceeds the maximum size")

        output_path.chmod(0o600)

        return RenderedPage(
            path=output_path,
            mime_type="image/jpeg",
            size_bytes=size_bytes,
            pixel_width=page_info.pixel_width,
            pixel_height=page_info.pixel_height,
            color_mode="RGB"
        )
    except PdfRenderError:
        output_path.unlink(missing_ok=True)
        raise
    except Exception as error:
        output_path.unlink(missing_ok=True)
        raise PdfRenderError("The PDF page could not be rendered") from error
