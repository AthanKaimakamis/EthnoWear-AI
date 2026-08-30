import json
from dataclasses import replace

import pytest

from ethnowear_worker_common.api.errors import WorkerApiContractError
from ethnowear_document_worker.api.models import ClaimResponse, OcrResultResponse
from ethnowear_document_worker.jobs.ocr import (
    OcrTextLimitError,
    build_ocr_result,
    validate_ocr_result_response,
)
from ethnowear_document_worker.ocr.models import (
    BoundingBox,
    FigureCandidate,
    OcrOutput,
    OcrWord,
)
from ethnowear_document_worker.ocr.serialization import OcrOutputLimitError


def output(raw_text: str = "Българска шевица") -> OcrOutput:
    word = OcrWord(
        page_number=1,
        block_number=1,
        paragraph_number=1,
        line_number=1,
        word_number=1,
        left=10,
        top=20,
        width=30,
        height=40,
        confidence=0.91,
        text="шевица",
    )
    return OcrOutput(
        raw_text=raw_text,
        mean_confidence=0.91,
        words=(word,),
        engine_name="tesseract",
        engine_version="5.5.0",
        language="bul",
    )


def claim() -> ClaimResponse:
    return ClaimResponse.model_validate({
        "jobId": 12,
        "jobType": "OCR",
        "claimToken": "opaque-token",
        "attempt": 1,
        "claimedAt": "2026-08-22T10:00:00Z",
        "leaseExpiresAt": "2026-08-22T10:02:00Z",
        "target": {
            "documentId": 7,
            "documentPageId": 21,
            "knowledgeChunkId": None,
            "inputAvailable": True,
        },
        "limits": {
            "maximumInputBytes": 25_000_000,
            "maximumPageCount": 2_000,
            "renderDpi": 300,
            "maximumPixelWidth": 20_000,
            "maximumPixelHeight": 20_000,
            "maximumPagePixels": 100_000_000,
            "maximumRenditionBytes": 25_000_000,
            "maximumOcrTextCharacters": 1_000_000,
            "maximumOcrOutputBytes": 10_000_000,
            "maximumOcrContextBytes": 16_000_000,
            "maximumQualityAssessmentPayloadBytes": 1_000_000,
            "maximumQualitySignals": 100,
            "maximumQualitySignalTypeCharacters": 100,
            "maximumQualitySignalTextCharacters": 500,
            "maximumQualitySummaryCharacters": 1_000,
            "maximumQualityLimitationsCharacters": 1_000,
            "maximumQualityMessageCharacters": 1_000,
            "maximumVisionAssessmentPayloadBytes": 10_000_000,
            "maximumVisionSuggestionCharacters": 2_000_000,
            "maximumVisionIssuesJsonCharacters": 4_000,
            "maximumVisionIssues": 100,
            "maximumVisionUncertainPassages": 100,
            "maximumVisionIssueCodeCharacters": 100,
            "maximumVisionExcerptCharacters": 500,
            "maximumVisionReasonCharacters": 1_000,
            "maximumVisionModelNameCharacters": 100,
            "maximumVisionModelVersionCharacters": 100,
            "maximumVisionPromptVersionCharacters": 100,
            "maximumIndexingContentCharacters": 10_000,
            "maximumEmbeddingDimensions": 4_096,
            "maximumEmbeddingModelCharacters": 100,
            "maximumVectorCollectionCharacters": 150,
            "maximumVectorPointIdCharacters": 255,
            "maximumFigureCandidates": 100,
            "maximumFigureCaptionCharacters": 2_000,
            "maximumPrintedFigureNumberCharacters": 100,
            "maximumFigureCropBytes": 25 * 1024 * 1024,
            "jobTimeoutSeconds": 1_800,
            "heartbeatIntervalSeconds": 30,
            "maximumLeaseSeconds": 300,
        },
    })


def test_build_ocr_result_maps_text_engine_confidence_and_json() -> None:
    request = build_ocr_result(
        output(),
        maximum_text_characters=1_000,
        maximum_output_bytes=10_000,
        oem=1,
        psm=3,
        preprocessed=False,
    )

    assert request.raw_text == "Българска шевица"
    assert request.ocr_engine == "tesseract"
    assert request.ocr_engine_version == "5.5.0"
    assert request.ocr_language == "bul"
    assert request.ocr_confidence == 0.91
    assert json.loads(request.parameters_json or "") == {
        "language": "bul",
        "oem": 1,
        "psm": 3,
        "preprocessed": False,
        "preprocessing": [],
        "strategy": "FULL_PAGE_PSM_3",
    }
    assert json.loads(request.structured_output_json or "")["schemaVersion"] == 2
    assert json.loads(request.structured_output_json or "")["words"][0][
        "text"
    ] == "шевица"


def test_build_ocr_result_supports_empty_page() -> None:
    empty = OcrOutput(
        raw_text="",
        mean_confidence=None,
        words=(),
        engine_name="tesseract",
        engine_version="5.5.0",
        language="bul",
    )

    request = build_ocr_result(
        empty,
        maximum_text_characters=1,
        maximum_output_bytes=500,
        oem=1,
        psm=3,
        preprocessed=False,
    )

    assert request.raw_text == ""
    assert request.ocr_confidence is None


def test_build_ocr_result_maps_figure_candidates() -> None:
    candidate = FigureCandidate(
        candidate_ordinal=1,
        bounds=BoundingBox(100, 200, 400, 300),
        normalized_x=0.1,
        normalized_y=0.2,
        normalized_width=0.4,
        normalized_height=0.3,
        detection_confidence=0.87,
        candidate_type="ILLUSTRATION",
        raw_caption_text="Обр. 12",
    )
    value = replace(output(), figure_candidates=(candidate,))

    request = build_ocr_result(
        value,
        maximum_text_characters=1_000,
        maximum_output_bytes=10_000,
        oem=1,
        psm=3,
        preprocessed=False,
    )

    assert request.figure_candidates[0].candidate_ordinal == 1
    assert request.figure_candidates[0].normalized_width == 0.4
    assert request.figure_candidates[0].raw_caption_text == "Обр. 12"


def test_build_ocr_result_enforces_text_limit() -> None:
    with pytest.raises(OcrTextLimitError, match="character count"):
        build_ocr_result(
            output("four"),
            maximum_text_characters=3,
            maximum_output_bytes=10_000,
            oem=1,
            psm=3,
            preprocessed=False,
        )


def test_build_ocr_result_enforces_structured_output_limit() -> None:
    with pytest.raises(OcrOutputLimitError, match="maximum size"):
        build_ocr_result(
            output(),
            maximum_text_characters=1_000,
            maximum_output_bytes=1,
            oem=1,
            psm=3,
            preprocessed=False,
        )


def test_validate_ocr_result_response_accepts_matching_identities() -> None:
    validate_ocr_result_response(
        OcrResultResponse(
            ocr_result_id=31,
            job_id=12,
            document_id=7,
            page_id=21,
            input_media_id=41,
            existing=False,
        ),
        claim(),
    )


@pytest.mark.parametrize(
    ("field", "value", "message"),
    [
        ("job_id", 99, "job identity"),
        ("document_id", 99, "document identity"),
        ("page_id", 99, "page identity"),
    ],
)
def test_validate_ocr_result_response_rejects_mismatched_identity(
    field: str,
    value: int,
    message: str,
) -> None:
    values = {
        "ocr_result_id": 31,
        "job_id": 12,
        "document_id": 7,
        "page_id": 21,
        "input_media_id": 41,
        "existing": False,
    }
    values[field] = value

    with pytest.raises(WorkerApiContractError, match=message):
        validate_ocr_result_response(
            OcrResultResponse(**values),
            claim(),
        )
