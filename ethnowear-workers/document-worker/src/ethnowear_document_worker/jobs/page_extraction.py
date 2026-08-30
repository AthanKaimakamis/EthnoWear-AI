import pymupdf

from ethnowear_document_worker.api.models import (
    ColorMode,
    ManifestPageRequest,
    ManifestPageResponse,
    ManifestRequest,
    ManifestResponse,
    RenditionMetadata,
    RenditionType,
)
from ethnowear_worker_common.api.errors import WorkerApiContractError
from ethnowear_document_worker.pdf.renderer import RenderedPage
from ethnowear_document_worker.pdf.inspector import PdfInspection


class PageExtractionTargetContractError(WorkerApiContractError):
    pass


def build_manifest(inspection: PdfInspection) -> ManifestRequest:
    return ManifestRequest(
        total_page_count=inspection.page_count,
        pages=tuple(
            ManifestPageRequest(
                pdf_page_index=page.pdf_page_index,
                page_sequence=page.page_sequence,
            )
            for page in inspection.pages
        ),
    )


def validate_manifest_response(
        response: ManifestResponse,
        expected_document_id: int,
        inspection: PdfInspection,
) -> None:
    if response.document_id != expected_document_id:
        raise WorkerApiContractError("Manifest document identity does not match claim")

    if response.page_count != inspection.page_count:
        raise WorkerApiContractError("Manifest response page count does not match PDF")

    if len(response.pages) != inspection.page_count:
        raise WorkerApiContractError("Manifest response entries do not match PDF")

    page_ids: set[int] = set()
    for expected, actual in zip(inspection.pages, response.pages, strict=True):
        if (actual.pdf_page_index != expected.pdf_page_index
                or actual.page_sequence != expected.page_sequence):
            raise WorkerApiContractError("Manifest response page identities are invalid")
        if actual.page_id in page_ids:
            raise WorkerApiContractError("Manifest response contains duplicate page IDs")
        page_ids.add(actual.page_id)


def select_rendition_pages(
        response: ManifestResponse,
        *,
        target_page_id: int | None,
        target_pdf_page_index: int | None,
) -> tuple[ManifestPageResponse, ...]:
    if target_page_id is None:
        return tuple(page for page in response.pages if page.rendition_required)

    if target_pdf_page_index is None:
        raise PageExtractionTargetContractError(
            "Targeted page extraction claim has no PDF page index"
        )

    target = next(
        (
            page for page in response.pages
            if page.page_id == target_page_id
            and page.pdf_page_index == target_pdf_page_index
        ),
        None,
    )
    if target is None:
        raise PageExtractionTargetContractError(
            "Page extraction manifest does not contain the requested target"
        )
    if not target.rendition_required:
        raise PageExtractionTargetContractError(
            "Page extraction manifest does not request the target rendition"
        )
    return (target,)


def build_rendition_metadata(
        page: ManifestPageResponse,
        rendered: RenderedPage,
        render_dpi: int
) -> RenditionMetadata:
    return RenditionMetadata(
        pdf_page_index=page.pdf_page_index,
        page_sequence=page.page_sequence,
        rendition_type=RenditionType.PDF_PAGE_RENDER,
        dpi=render_dpi,
        color_mode=ColorMode(rendered.color_mode),
        pixel_width=rendered.pixel_width,
        pixel_height=rendered.pixel_height,
        renderer_name="PyMuPDF",
        renderer_version=pymupdf.VersionBind,
    )
