from pathlib import Path
import math

import cv2
import numpy as np

from ethnowear_figure_worker.api.models import FigureCandidateResponse
from ethnowear_figure_worker.inspection.models import (
    CandidateDecision,
    CandidateRejectionReason,
)
from ethnowear_figure_worker.layout.models import (
    OcrBlockType,
    ParsedOcrLayout,
    PixelBounds,
)


class CandidateReevaluationError(ValueError):
    pass


def reevaluate_candidates(
    image_path: Path,
    candidates: tuple[FigureCandidateResponse, ...],
    layout: ParsedOcrLayout,
    *,
    maximum_candidates: int,
) -> tuple[CandidateDecision, ...]:
    if maximum_candidates <= 0:
        raise ValueError("Maximum candidate count must be positive")

    if len(candidates) > maximum_candidates:
        raise CandidateReevaluationError("Figure candidate count exceeds the maximum")

    grayscale = cv2.imread(str(image_path), cv2.IMREAD_GRAYSCALE)
    if grayscale is None:
        raise CandidateReevaluationError("Figure input could not be decoded")

    page_height, page_width = grayscale.shape

    return tuple(
        _reevaluate_candidate(
            candidate,
            grayscale,
            layout,
            page_width,
            page_height,
        )
        for candidate in candidates
    )


def _reevaluate_candidate(
    candidate: FigureCandidateResponse,
    grayscale: np.ndarray,
    layout: ParsedOcrLayout,
    page_width: int,
    page_height: int,
) -> CandidateDecision:
    bounds = _pixel_bounds(
        candidate,
        page_width,
        page_height,
    )

    if bounds is None:
        return _rejected(
            candidate,
            CandidateRejectionReason.INVALID_BOUNDS,
        )

    page_area = page_width * page_height
    area_ratio = bounds.area / page_area

    if area_ratio < 0.005:
        return _rejected(
            candidate,
            CandidateRejectionReason.AREA_TOO_SMALL,
            bounds,
        )

    if _looks_like_page_border(bounds, page_width, page_height):
        return _rejected(
            candidate,
            CandidateRejectionReason.PAGE_BORDER,
            bounds,
        )

    if _looks_like_separator(bounds, page_width, page_height):
        return _rejected(
            candidate,
            CandidateRejectionReason.DECORATIVE_SEPARATOR,
            bounds,
        )

    text_coverage = _text_coverage(bounds, layout)
    if text_coverage >= 0.65:
        return _rejected(
            candidate,
            CandidateRejectionReason.TEXT_DOMINATED,
            bounds,
        )

    patch = grayscale[
        bounds.top:bounds.bottom,
        bounds.left:bounds.right,
    ]

    if patch.size == 0:
        return _rejected(
            candidate,
            CandidateRejectionReason.INVALID_BOUNDS,
            bounds,
        )

    ink_ratio = float(np.count_nonzero(patch < 220)) / patch.size
    texture = float(np.std(patch))
    edge_density = _edge_density(patch)

    if (
        texture < 12.0
        or edge_density < 0.01
        or ink_ratio < 0.015
    ):
        return _rejected(
            candidate,
            CandidateRejectionReason.LOW_VISUAL_CONTENT,
            bounds,
        )

    source_confidence = candidate.detection_confidence or 0.5
    confidence = (
        source_confidence * 0.35
        + _bounded_ratio(texture, 12.0, 70.0) * 0.25
        + _bounded_ratio(edge_density, 0.01, 0.20) * 0.20
        + _bounded_ratio(ink_ratio, 0.015, 0.45) * 0.20
    )

    confidence *= 1.0 - min(0.50, text_coverage * 0.50)

    return CandidateDecision(
        candidate_id=candidate.candidate_id,
        candidate_ordinal=candidate.candidate_ordinal,
        bounds=bounds,
        accepted=True,
        confidence=round(
            min(1.0, max(0.0, confidence)),
            4,
        ),
        rejection_reason=None,
        raw_caption_text=candidate.raw_caption_text,
    )


def _pixel_bounds(
    candidate: FigureCandidateResponse,
    page_width: int,
    page_height: int,
) -> PixelBounds | None:
    left = _lower_pixel(candidate.normalized_x, page_width)
    top = _lower_pixel(candidate.normalized_y, page_height)
    right = _upper_pixel(
        (
            candidate.normalized_x
            + candidate.normalized_width
        ),
        page_width,
    )
    bottom = _upper_pixel(
        (
            candidate.normalized_y
            + candidate.normalized_height
        ),
        page_height,
    )

    left = max(0, min(left, page_width))
    top = max(0, min(top, page_height))
    right = max(0, min(right, page_width))
    bottom = max(0, min(bottom, page_height))

    if right <= left or bottom <= top:
        return None

    return PixelBounds(
        left=left,
        top=top,
        width=right - left,
        height=bottom - top,
    )


def _lower_pixel(value: float, dimension: int) -> int:
    return math.floor(value * dimension + 1e-9)


def _upper_pixel(value: float, dimension: int) -> int:
    return math.ceil(value * dimension - 1e-9)


def _looks_like_page_border(
    bounds: PixelBounds,
    page_width: int,
    page_height: int,
) -> bool:
    touches_left = bounds.left <= page_width * 0.01
    touches_right = bounds.right >= page_width * 0.99
    touches_top = bounds.top <= page_height * 0.01
    touches_bottom = bounds.bottom >= page_height * 0.99

    return (
        touches_left
        and touches_right
        and touches_top
        and touches_bottom
    )


def _looks_like_separator(
    bounds: PixelBounds,
    page_width: int,
    page_height: int,
) -> bool:
    return (
        bounds.width >= page_width * 0.35
        and bounds.height <= page_height * 0.02
    ) or (
        bounds.height >= page_height * 0.35
        and bounds.width <= page_width * 0.02
    )


def _text_coverage(
    candidate: PixelBounds,
    layout: ParsedOcrLayout,
) -> float:
    # Tesseract can emit a page-spanning parent block even when its actual
    # words occupy only the text above and below an illustration.  Treating
    # that parent rectangle as filled text rejects every nested figure.  Word
    # boxes are the authoritative fine-grained evidence when they are present;
    # the block fallback keeps older layout payloads usable.
    if layout.words:
        covered_area = sum(
            _intersection_area(candidate, word.bounds)
            for word in layout.words
        )
        return min(1.0, covered_area / candidate.area)

    covered_area = 0

    for block in layout.blocks:
        if block.block_type in {
            OcrBlockType.ILLUSTRATION,
            OcrBlockType.CAPTION,
        }:
            continue

        covered_area += _intersection_area(
            candidate,
            block.bounds,
        )

    return min(1.0, covered_area / candidate.area)


def _intersection_area(
    left: PixelBounds,
    right: PixelBounds,
) -> int:
    intersection_width = max(
        0,
        min(left.right, right.right)
        - max(left.left, right.left),
    )
    intersection_height = max(
        0,
        min(left.bottom, right.bottom)
        - max(left.top, right.top),
    )

    return intersection_width * intersection_height


def _edge_density(patch: np.ndarray) -> float:
    edges = cv2.Canny(patch, 80, 180)
    return float(np.count_nonzero(edges)) / edges.size


def _bounded_ratio(
    value: float,
    minimum: float,
    maximum: float,
) -> float:
    if maximum <= minimum:
        raise ValueError("Confidence range must be increasing")

    return min(
        1.0,
        max(0.0, (value - minimum) / (maximum - minimum)),
    )


def _rejected(
    candidate: FigureCandidateResponse,
    reason: CandidateRejectionReason,
    bounds: PixelBounds | None = None,
) -> CandidateDecision:
    return CandidateDecision(
        candidate_id=candidate.candidate_id,
        candidate_ordinal=candidate.candidate_ordinal,
        bounds=bounds,
        accepted=False,
        confidence=0.0,
        rejection_reason=reason,
        raw_caption_text=candidate.raw_caption_text,
    )
