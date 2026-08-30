import pytest

from ethnowear_document_worker.layout.detector import LayoutAnalysis
from ethnowear_document_worker.layout.models import LayoutRegion
from ethnowear_document_worker.ocr.candidates import build_figure_candidates
from ethnowear_document_worker.ocr.models import (
    BoundingBox,
    OcrBlock,
    OcrBlockType,
)


def illustration(
        region_id: int,
        bounds: BoundingBox,
        confidence: float,
        reading_order: int,
) -> LayoutRegion:
    return LayoutRegion(
        region_id,
        OcrBlockType.ILLUSTRATION,
        bounds,
        reading_order,
        confidence,
    )


def caption(
        block_id: int,
        bounds: BoundingBox,
        text: str,
        reading_order: int,
) -> OcrBlock:
    return OcrBlock(
        block_id=block_id,
        block_type=OcrBlockType.CAPTION,
        bounds=bounds,
        reading_order=reading_order,
        text=text,
        confidence=0.9,
    )


def test_builds_normalized_candidate_with_caption_below() -> None:
    region = illustration(
        1,
        BoundingBox(100, 200, 400, 300),
        0.87,
        2,
    )
    layout = LayoutAnalysis(1_000, 1_000, (region,))
    blocks = (
        caption(
            2,
            BoundingBox(150, 520, 300, 40),
            "Обр. 12 — Престилка",
            3,
        ),
    )

    candidates = build_figure_candidates(
        layout,
        blocks,
        maximum_candidates=10,
        maximum_caption_characters=2_000,
    )

    assert len(candidates) == 1
    assert candidates[0].normalized_x == 0.1
    assert candidates[0].normalized_y == 0.2
    assert candidates[0].normalized_width == 0.4
    assert candidates[0].normalized_height == 0.3
    assert candidates[0].raw_caption_text == "Обр. 12 — Престилка"


def test_does_not_associate_unrelated_caption() -> None:
    region = illustration(
        1,
        BoundingBox(100, 200, 300, 300),
        0.8,
        1,
    )
    layout = LayoutAnalysis(1_000, 1_000, (region,))
    blocks = (
        caption(
            2,
            BoundingBox(700, 510, 200, 40),
            "Unrelated",
            2,
        ),
    )

    candidates = build_figure_candidates(
        layout,
        blocks,
        maximum_candidates=10,
        maximum_caption_characters=2_000,
    )

    assert candidates[0].raw_caption_text is None


def test_bounds_candidates_and_preserves_reading_order() -> None:
    regions = (
        illustration(1, BoundingBox(50, 100, 200, 200), 0.7, 1),
        illustration(2, BoundingBox(50, 400, 200, 200), 0.9, 2),
        illustration(3, BoundingBox(50, 700, 200, 200), 0.8, 3),
    )
    layout = LayoutAnalysis(1_000, 1_000, regions)

    candidates = build_figure_candidates(
        layout,
        (),
        maximum_candidates=2,
        maximum_caption_characters=2_000,
    )

    assert [candidate.bounds.top for candidate in candidates] == [400, 700]
    assert [candidate.candidate_ordinal for candidate in candidates] == [1, 2]


def test_returns_empty_result_for_text_only_page() -> None:
    layout = LayoutAnalysis(1_000, 1_000, ())

    assert build_figure_candidates(
        layout,
        (),
        maximum_candidates=10,
        maximum_caption_characters=2_000,
    ) == ()


def test_rejects_invalid_candidate_limit() -> None:
    layout = LayoutAnalysis(1_000, 1_000, ())

    with pytest.raises(ValueError, match="must be positive"):
        build_figure_candidates(
            layout,
            (),
            maximum_candidates=0,
            maximum_caption_characters=2_000,
        )


def test_bounds_caption_to_backend_limit() -> None:
    region = illustration(
        1,
        BoundingBox(100, 200, 400, 300),
        0.8,
        1,
    )
    layout = LayoutAnalysis(1_000, 1_000, (region,))
    blocks = (
        caption(2, BoundingBox(100, 510, 400, 40), "123456", 2),
    )

    candidates = build_figure_candidates(
        layout,
        blocks,
        maximum_candidates=10,
        maximum_caption_characters=4,
    )

    assert candidates[0].raw_caption_text == "1234"
