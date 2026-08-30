from dataclasses import FrozenInstanceError

import pytest

from ethnowear_document_worker.ocr.models import (
    BoundingBox,
    FigureCandidate,
    OcrOutput,
    OcrWord,
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
        confidence=0.875,
        text="шевица",
    )


def test_ocr_word_preserves_normalized_layout_data() -> None:
    result = word()

    assert result.text == "шевица"
    assert result.confidence == 0.875
    assert (result.left, result.top, result.width, result.height) == (
        10, 20, 30, 40
    )


def test_ocr_output_preserves_engine_metadata_and_words() -> None:
    result = OcrOutput(
        raw_text="Българска шевица",
        mean_confidence=0.875,
        words=(word(),),
        engine_name="tesseract",
        engine_version="5.5.0",
        language="bul",
    )

    assert result.words == (word(),)
    assert result.engine_name == "tesseract"
    assert result.language == "bul"


def test_ocr_models_are_immutable_and_slotted() -> None:
    result = word()

    with pytest.raises(FrozenInstanceError):
        result.text = "changed"  # type: ignore[misc]

    assert not hasattr(result, "__dict__")


def test_figure_candidate_preserves_pixel_and_normalized_bounds() -> None:
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

    assert candidate.bounds.right == 500
    assert candidate.normalized_width == 0.4
    assert candidate.candidate_type == "ILLUSTRATION"
