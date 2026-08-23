import math
from dataclasses import dataclass
from pathlib import Path

import pymupdf


class PdfInspectionError(RuntimeError):
    pass


@dataclass(frozen=True, slots=True)
class PdfPageInfo:
    pdf_page_index: int
    page_sequence: int
    pixel_width: int
    pixel_height: int


@dataclass(frozen=True, slots=True)
class PdfInspection:
    page_count: int
    pages: tuple[PdfPageInfo, ...]


def inspect_pdf(
        path: Path,
        maximum_page_count: int,
        render_dpi: int,
        maximum_pixel_width: int,
        maximum_pixel_height: int,
        maximum_page_pixels: int,
) -> PdfInspection:
    if min(
            maximum_page_count,
            render_dpi,
            maximum_pixel_width,
            maximum_pixel_height,
            maximum_page_pixels,
    ) <= 0:
        raise ValueError("PDF inspection limits must be positive")

    try:
        document = pymupdf.open(path)
    except Exception as error:
        raise PdfInspectionError("The PDF could not be opened") from error

    try:
        if document.needs_pass:
            raise PdfInspectionError("Password-protected PDFs are not supported")

        page_count = document.page_count

        if page_count <= 0:
            raise PdfInspectionError("The PDF contains no pages")

        if page_count > maximum_page_count:
            raise PdfInspectionError("The PDF page count exceeds the maximum")

        pages: list[PdfPageInfo] = []

        for index in range(page_count):
            page = document.load_page(index)
            rectangle = page.rect

            pixel_width = math.ceil(rectangle.width * render_dpi / 72)
            pixel_height = math.ceil(rectangle.height * render_dpi / 72)

            if pixel_width > maximum_pixel_width or pixel_height > maximum_pixel_height:
                raise PdfInspectionError("PDF page pixel dimensions exceed the maximum")

            if pixel_width * pixel_height > maximum_page_pixels:
                raise PdfInspectionError("PDF page pixel count exceeds the maximum")

            pages.append(
                PdfPageInfo(
                    pdf_page_index=index,
                    page_sequence=index + 1,
                    pixel_width=pixel_width,
                    pixel_height=pixel_height,
                )
            )

        return PdfInspection(
            page_count=page_count,
            pages=tuple(pages),
        )
    except PdfInspectionError:
        raise
    except Exception as error:
        raise PdfInspectionError("The PDF could not be inspected") from error
    finally:
        document.close()
