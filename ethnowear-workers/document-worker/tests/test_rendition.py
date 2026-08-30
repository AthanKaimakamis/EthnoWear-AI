from pathlib import Path

from ethnowear_document_worker.api.models import ManifestPageResponse, RenditionType
from ethnowear_document_worker.jobs.page_extraction import build_rendition_metadata
from ethnowear_document_worker.pdf.renderer import RenderedPage


def test_build_rendition_metadata_matches_spring_contract() -> None:
    page = ManifestPageResponse(
        page_id=21,
        pdf_page_index=0,
        page_sequence=1,
        rendition_required=True,
    )
    rendered = RenderedPage(
        path=Path("page-21.jpg"),
        mime_type="image/jpeg",
        size_bytes=500,
        pixel_width=300,
        pixel_height=600,
        color_mode="RGB",
    )

    metadata = build_rendition_metadata(page, rendered, render_dpi=300)
    serialized = metadata.model_dump(by_alias=True, mode="json")

    assert serialized["pdfPageIndex"] == 0
    assert serialized["pageSequence"] == 1
    assert serialized["renditionType"] == "PDF_PAGE_RENDER"
    assert serialized["dpi"] == 300
    assert serialized["colorMode"] == "RGB"
    assert serialized["pixelWidth"] == 300
    assert serialized["pixelHeight"] == 600
    assert serialized["rendererName"] == "PyMuPDF"
    assert serialized["rendererVersion"]
    assert metadata.rendition_type is RenditionType.PDF_PAGE_RENDER


def test_rendition_response_maps_idempotent_retry() -> None:
    from ethnowear_document_worker.api.models import RenditionResponse

    response = RenditionResponse.model_validate(
        {
            "pageId": 21,
            "pageMediaId": 31,
            "mediaAssetId": 41,
            "renditionType": "PDF_PAGE_RENDER",
            "existing": True,
        }
    )

    assert response.page_id == 21
    assert response.existing is True
