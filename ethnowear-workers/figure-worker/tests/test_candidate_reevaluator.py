from pathlib import Path

import cv2
import numpy as np
import pytest

from ethnowear_figure_worker.api.models import FigureCandidateResponse
from ethnowear_figure_worker.inspection.models import CandidateRejectionReason
from ethnowear_figure_worker.inspection.reevaluator import (
    CandidateReevaluationError,
    reevaluate_candidates,
)
from ethnowear_figure_worker.layout.models import (
    LayoutBlock,
    LayoutWord,
    OcrBlockType,
    ParsedOcrLayout,
    PixelBounds,
)


def candidate(
    *,
    candidate_id: int = 1,
    ordinal: int = 1,
    x: float = 0.20,
    y: float = 0.20,
    width: float = 0.40,
    height: float = 0.40,
) -> FigureCandidateResponse:
    return FigureCandidateResponse(
        candidate_id=candidate_id,
        candidate_ordinal=ordinal,
        normalized_x=x,
        normalized_y=y,
        normalized_width=width,
        normalized_height=height,
        raw_caption_text="Обр. 12 — Престилка",
        detection_confidence=0.85,
    )


def layout(
    *blocks: LayoutBlock,
    words: tuple[LayoutWord, ...] = (),
) -> ParsedOcrLayout:
    return ParsedOcrLayout(
        schema_version=2,
        selected_strategy="REGION_PSM_6",
        blocks=blocks,
        words=words,
    )


def text_block(bounds: PixelBounds) -> LayoutBlock:
    return LayoutBlock(
        block_id=1,
        block_type=OcrBlockType.BODY_TEXT,
        reading_order=0,
        bounds=bounds,
        confidence=0.90,
        text="Текстов блок",
        word_count=2,
    )


def save_page(path: Path, image: np.ndarray) -> None:
    assert cv2.imwrite(str(path), image)


def test_accepts_textured_visual_candidate(tmp_path: Path) -> None:
    page = np.full((200, 200), 255, dtype=np.uint8)
    rng = np.random.default_rng(42)
    page[40:120, 40:120] = rng.integers(
        0,
        256,
        size=(80, 80),
        dtype=np.uint8,
    )
    path = tmp_path / "page.png"
    save_page(path, page)

    result = reevaluate_candidates(
        path,
        (candidate(),),
        layout(),
        maximum_candidates=10,
    )

    assert len(result) == 1
    assert result[0].accepted is True
    assert result[0].bounds == PixelBounds(40, 40, 80, 80)
    assert result[0].confidence > 0.0
    assert result[0].rejection_reason is None
    assert result[0].raw_caption_text == "Обр. 12 — Престилка"


def test_zero_candidates_is_successful(tmp_path: Path) -> None:
    page = np.full((200, 200), 255, dtype=np.uint8)
    path = tmp_path / "page.png"
    save_page(path, page)

    assert reevaluate_candidates(
        path,
        (),
        layout(),
        maximum_candidates=10,
    ) == ()


def test_rejects_candidate_count_above_limit(tmp_path: Path) -> None:
    page = np.full((200, 200), 255, dtype=np.uint8)
    path = tmp_path / "page.png"
    save_page(path, page)

    with pytest.raises(CandidateReevaluationError, match="count exceeds"):
        reevaluate_candidates(
            path,
            (
                candidate(candidate_id=1, ordinal=1),
                candidate(candidate_id=2, ordinal=2),
            ),
            layout(),
            maximum_candidates=1,
        )


def test_rejects_text_dominated_candidate(tmp_path: Path) -> None:
    page = np.full((200, 200), 255, dtype=np.uint8)
    path = tmp_path / "page.png"
    save_page(path, page)

    result = reevaluate_candidates(
        path,
        (candidate(),),
        layout(text_block(PixelBounds(40, 40, 80, 80))),
        maximum_candidates=10,
    )

    assert result[0].accepted is False
    assert result[0].rejection_reason is CandidateRejectionReason.TEXT_DOMINATED


def test_page_spanning_text_block_does_not_hide_figure_when_words_are_outside(
        tmp_path: Path,
) -> None:
    page = np.full((200, 200), 255, dtype=np.uint8)
    rng = np.random.default_rng(42)
    page[40:120, 40:120] = rng.integers(
        0,
        256,
        size=(80, 80),
        dtype=np.uint8,
    )
    path = tmp_path / "page.png"
    save_page(path, page)

    result = reevaluate_candidates(
        path,
        (candidate(),),
        layout(
            text_block(PixelBounds(0, 0, 200, 200)),
            words=(
                LayoutWord(
                    bounds=PixelBounds(10, 10, 30, 10),
                    confidence=0.9,
                    text="header",
                ),
                LayoutWord(
                    bounds=PixelBounds(10, 160, 30, 10),
                    confidence=0.9,
                    text="footer",
                ),
            ),
        ),
        maximum_candidates=10,
    )

    assert result[0].accepted is True


def test_rejects_low_visual_content(tmp_path: Path) -> None:
    page = np.full((200, 200), 255, dtype=np.uint8)
    path = tmp_path / "page.png"
    save_page(path, page)

    result = reevaluate_candidates(
        path,
        (candidate(),),
        layout(),
        maximum_candidates=10,
    )

    assert result[0].accepted is False
    assert result[0].rejection_reason is CandidateRejectionReason.LOW_VISUAL_CONTENT


def test_rejects_full_page_border_candidate(tmp_path: Path) -> None:
    page = np.full((200, 200), 255, dtype=np.uint8)
    path = tmp_path / "page.png"
    save_page(path, page)

    result = reevaluate_candidates(
        path,
        (candidate(x=0.0, y=0.0, width=1.0, height=1.0),),
        layout(),
        maximum_candidates=10,
    )

    assert result[0].accepted is False
    assert result[0].rejection_reason is CandidateRejectionReason.PAGE_BORDER


def test_rejects_decorative_separator(tmp_path: Path) -> None:
    page = np.full((200, 200), 255, dtype=np.uint8)
    path = tmp_path / "page.png"
    save_page(path, page)

    result = reevaluate_candidates(
        path,
        (candidate(x=0.20, y=0.40, width=0.60, height=0.01),),
        layout(),
        maximum_candidates=10,
    )

    assert result[0].accepted is False
    assert result[0].rejection_reason is CandidateRejectionReason.DECORATIVE_SEPARATOR


def test_rejects_small_candidate(tmp_path: Path) -> None:
    page = np.full((200, 200), 255, dtype=np.uint8)
    path = tmp_path / "page.png"
    save_page(path, page)

    result = reevaluate_candidates(
        path,
        (candidate(width=0.05, height=0.05),),
        layout(),
        maximum_candidates=10,
    )

    assert result[0].accepted is False
    assert result[0].rejection_reason is CandidateRejectionReason.AREA_TOO_SMALL


def test_rejects_non_positive_candidate_limit(tmp_path: Path) -> None:
    path = tmp_path / "page.png"
    save_page(path, np.full((200, 200), 255, dtype=np.uint8))

    with pytest.raises(ValueError, match="count must be positive"):
        reevaluate_candidates(
            path,
            (),
            layout(),
            maximum_candidates=0,
        )


def test_rejects_undecodable_image(tmp_path: Path) -> None:
    path = tmp_path / "page.png"
    path.write_bytes(b"not an image")

    with pytest.raises(CandidateReevaluationError, match="could not be decoded"):
        reevaluate_candidates(
            path,
            (),
            layout(),
            maximum_candidates=10,
        )
