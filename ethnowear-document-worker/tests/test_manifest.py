import pytest
from pydantic import ValidationError

from ethnowear_worker.api.models import (
    ManifestPageRequest,
    ManifestRequest,
    ManifestResponse,
)
from ethnowear_worker.api.errors import WorkerApiContractError
from ethnowear_worker.jobs.page_extraction import (
    build_manifest,
    validate_manifest_response,
)
from ethnowear_worker.pdf.inspector import PdfInspection, PdfPageInfo


def inspection() -> PdfInspection:
    return PdfInspection(
        page_count=2,
        pages=(
            PdfPageInfo(0, 1, 300, 600),
            PdfPageInfo(1, 2, 600, 300),
        ),
    )


def test_build_manifest_serializes_contiguous_page_identities() -> None:
    manifest = build_manifest(inspection())

    assert manifest.model_dump(by_alias=True, mode="json") == {
        "totalPageCount": 2,
        "pages": [
            {"pdfPageIndex": 0, "pageSequence": 1},
            {"pdfPageIndex": 1, "pageSequence": 2},
        ],
    }


def test_manifest_rejects_non_contiguous_page_identities() -> None:
    with pytest.raises(ValidationError):
        ManifestRequest(
            total_page_count=2,
            pages=(
                ManifestPageRequest(pdf_page_index=0, page_sequence=1),
                ManifestPageRequest(pdf_page_index=2, page_sequence=2),
            ),
        )


def test_manifest_response_maps_stable_page_ids() -> None:
    response = ManifestResponse.model_validate(
        {
            "documentId": 7,
            "pageCount": 2,
            "pages": [
                {
                    "pageId": 21,
                    "pdfPageIndex": 0,
                    "pageSequence": 1,
                    "renditionRequired": False,
                },
                {
                    "pageId": 22,
                    "pdfPageIndex": 1,
                    "pageSequence": 2,
                    "renditionRequired": True,
                },
            ],
        }
    )

    assert response.document_id == 7
    assert response.pages[0].page_id == 21
    assert response.pages[1].rendition_required is True


def test_manifest_response_is_cross_checked_against_claim_and_pdf() -> None:
    response = ManifestResponse.model_validate({
        "documentId": 7,
        "pageCount": 2,
        "pages": [
            {"pageId": 21, "pdfPageIndex": 0, "pageSequence": 1,
             "renditionRequired": True},
            {"pageId": 22, "pdfPageIndex": 1, "pageSequence": 2,
             "renditionRequired": True},
        ],
    })

    validate_manifest_response(response, 7, inspection())


@pytest.mark.parametrize("change", ["document", "count", "identity", "duplicate"])
def test_manifest_response_rejects_contract_mismatch(change: str) -> None:
    payload = {
        "documentId": 7,
        "pageCount": 2,
        "pages": [
            {"pageId": 21, "pdfPageIndex": 0, "pageSequence": 1,
             "renditionRequired": True},
            {"pageId": 22, "pdfPageIndex": 1, "pageSequence": 2,
             "renditionRequired": True},
        ],
    }
    if change == "document":
        payload["documentId"] = 8
    elif change == "count":
        payload["pageCount"] = 3
    elif change == "identity":
        payload["pages"][1]["pdfPageIndex"] = 3
    else:
        payload["pages"][1]["pageId"] = 21

    with pytest.raises(WorkerApiContractError):
        validate_manifest_response(
            ManifestResponse.model_validate(payload), 7, inspection()
        )
