import json

import pytest

from ethnowear_document_worker.ocr.models import (
    BoundingBox,
    FigureCandidate,
    OcrOutput,
    OcrWord,
)
from ethnowear_document_worker.ocr.serialization import (
    OcrOutputLimitError,
    build_parameters,
    build_structured_output,
)


def word() -> OcrWord:
    return OcrWord(
        page_number=1,
        block_number=2,
        paragraph_number=3,
        line_number=4,
        word_number=5,
        left=10,
        top=20,
        width=30,
        height=40,
        confidence=0.87654,
        text="шевица",
    )


def output() -> OcrOutput:
    return OcrOutput(
        raw_text="шевица",
        mean_confidence=0.87654,
        words=(word(),),
        engine_name="tesseract",
        engine_version="5.5.0",
        language="bul",
    )


def test_build_structured_output_preserves_cyrillic_and_layout() -> None:
    serialized = build_structured_output(output(), maximum_bytes=10_000)
    payload = json.loads(serialized)

    assert "шевица" in serialized
    assert "\\u0448" not in serialized
    assert payload["schemaVersion"] == 2
    assert payload["selectedStrategy"] == "FULL_PAGE_PSM_3"
    assert payload["warnings"] == []
    assert payload["words"] == [{
            "page": 1,
            "block": 2,
            "paragraph": 3,
            "line": 4,
            "word": 5,
            "left": 10,
            "top": 20,
            "width": 30,
            "height": 40,
            "confidence": 0.8765,
            "text": "шевица",
        }]


def test_build_structured_output_represents_empty_page() -> None:
    empty = OcrOutput("", None, (), "tesseract", "5.5.0", "bul")
    payload = json.loads(build_structured_output(empty, maximum_bytes=500))
    assert payload["schemaVersion"] == 2
    assert payload["words"] == []


def test_build_structured_output_preserves_figure_candidate_layout() -> None:
    candidate = FigureCandidate(
        candidate_ordinal=1,
        bounds=BoundingBox(100, 200, 400, 300),
        normalized_x=0.1,
        normalized_y=0.2,
        normalized_width=0.4,
        normalized_height=0.3,
        detection_confidence=0.87654,
        candidate_type="ILLUSTRATION",
        raw_caption_text="Обр. 12",
    )
    value = output()
    value = OcrOutput(
        raw_text=value.raw_text,
        mean_confidence=value.mean_confidence,
        words=value.words,
        engine_name=value.engine_name,
        engine_version=value.engine_version,
        language=value.language,
        figure_candidates=(candidate,),
    )

    payload = json.loads(build_structured_output(value, maximum_bytes=10_000))

    assert payload["figureCandidates"] == [{
        "candidateOrdinal": 1,
        "candidateType": "ILLUSTRATION",
        "left": 100,
        "top": 200,
        "width": 400,
        "height": 300,
        "normalizedX": 0.1,
        "normalizedY": 0.2,
        "normalizedWidth": 0.4,
        "normalizedHeight": 0.3,
        "detectionConfidence": 0.8765,
        "rawCaptionText": "Обр. 12",
    }]


def test_build_structured_output_enforces_exact_utf8_byte_limit() -> None:
    serialized = build_structured_output(output(), maximum_bytes=10_000)
    exact_size = len(serialized.encode("utf-8"))

    assert build_structured_output(output(), exact_size) == serialized

    with pytest.raises(OcrOutputLimitError, match="maximum size"):
        build_structured_output(output(), exact_size - 1)


def test_build_structured_output_rejects_invalid_limit() -> None:
    with pytest.raises(ValueError, match="must be positive"):
        build_structured_output(output(), maximum_bytes=0)


def test_build_parameters_is_deterministic() -> None:
    assert build_parameters(
        language="bul+eng",
        oem=1,
        psm=3,
        preprocessed=False,
    ) == (
        '{"language":"bul+eng","oem":1,"preprocessed":false,'
        '"preprocessing":[],"psm":3,"strategy":null}'
    )
