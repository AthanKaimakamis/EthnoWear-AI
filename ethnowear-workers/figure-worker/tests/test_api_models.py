import pytest
from pydantic import ValidationError

from ethnowear_figure_worker.api.models import (
    ClaimRequest,
    FigureCropRequest,
    FigureExtractionContextResponse,
)
from ethnowear_worker_common.api.models import WorkerJobType


def context_payload() -> dict[str, object]:
    return {
        "jobId": 41,
        "documentId": 30,
        "documentPageId": 98,
        "documentPageMediaId": 501,
        "inputMediaAssetId": 601,
        "ocrResultId": 701,
        "ocrLayoutJson": '{"schemaVersion":2}',
        "candidates": [
            {
                "candidateId": 801,
                "candidateOrdinal": 1,
                "normalizedX": 0.1,
                "normalizedY": 0.2,
                "normalizedWidth": 0.5,
                "normalizedHeight": 0.4,
                "rawCaptionText": "Обр. 12 — Престилка",
                "detectionConfidence": 0.87,
            }
        ],
        "maximumCaptionCharacters": 2_000,
        "maximumPrintedNumberCharacters": 100,
        "maximumCropBytes": 25 * 1024 * 1024,
    }


def test_claim_supports_only_figure_extraction() -> None:
    request = ClaimRequest(
        worker_id="figure-worker-1",
        lease_seconds=120,
    )

    assert request.supported_job_types == (
        WorkerJobType.EXTRACT_PAGE_FIGURES,
    )


def test_context_maps_spring_contract() -> None:
    context = FigureExtractionContextResponse.model_validate(
        context_payload()
    )

    assert context.document_page_id == 98
    assert context.candidates[0].candidate_id == 801
    assert context.candidates[0].raw_caption_text == (
        "Обр. 12 — Престилка"
    )


def test_context_rejects_duplicate_candidate_ids() -> None:
    payload = context_payload()
    payload["candidates"] = [
        payload["candidates"][0],  # type: ignore[index]
        {
            **payload["candidates"][0],  # type: ignore[index]
            "candidateOrdinal": 2,
        },
    ]

    with pytest.raises(
        ValidationError,
        match="candidate IDs must be unique",
    ):
        FigureExtractionContextResponse.model_validate(payload)


def test_context_rejects_candidate_outside_page() -> None:
    payload = context_payload()
    payload["candidates"][0]["normalizedX"] = 0.8  # type: ignore[index]

    with pytest.raises(
        ValidationError,
        match="exceeds the page width",
    ):
        FigureExtractionContextResponse.model_validate(payload)


def test_crop_request_serializes_for_multipart_metadata() -> None:
    request = FigureCropRequest(
        candidate_id=801,
        figure_ordinal=1,
        printed_figure_number="12",
        raw_caption_text="Обр. 12 — Престилка",
    )

    assert request.model_dump(by_alias=True, mode="json") == {
        "candidateId": 801,
        "figureOrdinal": 1,
        "printedFigureNumber": "12",
        "rawCaptionText": "Обр. 12 — Престилка",
    }