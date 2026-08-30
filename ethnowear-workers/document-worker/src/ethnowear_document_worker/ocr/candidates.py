from ethnowear_document_worker.layout.detector import LayoutAnalysis
from ethnowear_document_worker.layout.models import LayoutRegion
from ethnowear_document_worker.ocr.models import (
    FigureCandidate,
    OcrBlock,
    OcrBlockType,
)


def build_figure_candidates(
        layout: LayoutAnalysis,
        blocks: tuple[OcrBlock, ...],
        *,
        maximum_candidates: int,
        maximum_caption_characters: int,
) -> tuple[FigureCandidate, ...]:
    if maximum_candidates <= 0:
        raise ValueError("Maximum figure candidates must be positive")

    if maximum_caption_characters <= 0:
        raise ValueError("Maximum figure caption length must be positive")

    captions = tuple(
        block
        for block in blocks
        if block.block_type is OcrBlockType.CAPTION
        and block.text.strip()
    )
    selected = sorted(
        layout.illustration_regions,
        key=lambda region: (
            -(region.detection_confidence or 0.0),
            region.reading_order,
        ),
    )[:maximum_candidates]
    selected.sort(key=lambda region: region.reading_order)

    return tuple(
        _candidate(
            ordinal,
            region,
            captions,
            layout,
            maximum_caption_characters,
        )
        for ordinal, region in enumerate(selected, start=1)
    )


def _candidate(
        ordinal: int,
        region: LayoutRegion,
        captions: tuple[OcrBlock, ...],
        layout: LayoutAnalysis,
        maximum_caption_characters: int,
) -> FigureCandidate:
    bounds = region.bounds
    caption = _nearest_caption(region, captions, layout.height)
    caption_text = caption.text.strip() if caption is not None else None

    if caption_text:
        caption_text = caption_text[:maximum_caption_characters]

    return FigureCandidate(
        candidate_ordinal=ordinal,
        bounds=bounds,
        normalized_x=_normalize(bounds.left, layout.width),
        normalized_y=_normalize(bounds.top, layout.height),
        normalized_width=_normalize(bounds.width, layout.width),
        normalized_height=_normalize(bounds.height, layout.height),
        detection_confidence=region.detection_confidence,
        candidate_type="ILLUSTRATION",
        raw_caption_text=caption_text,
    )


def _nearest_caption(
        illustration: LayoutRegion,
        captions: tuple[OcrBlock, ...],
        page_height: int,
) -> OcrBlock | None:
    matches: list[tuple[int, int, int, OcrBlock]] = []

    for caption in captions:
        overlap = max(
            0,
            min(illustration.bounds.right, caption.bounds.right)
            - max(illustration.bounds.left, caption.bounds.left),
        )
        minimum_width = min(
            illustration.bounds.width,
            caption.bounds.width,
        )
        if minimum_width <= 0 or overlap / minimum_width < 0.25:
            continue

        distance_below = caption.bounds.top - illustration.bounds.bottom
        distance_above = illustration.bounds.top - caption.bounds.bottom
        if 0 <= distance_below <= page_height * 0.12:
            matches.append((
                0,
                int(distance_below),
                caption.reading_order,
                caption,
            ))
        elif 0 <= distance_above <= page_height * 0.08:
            matches.append((
                1,
                int(distance_above),
                caption.reading_order,
                caption,
            ))

    if not matches:
        return None

    return min(matches, key=lambda item: item[:3])[3]


def _normalize(value: int, total: int) -> float:
    if total <= 0:
        raise ValueError("Page dimension must be positive")

    return round(min(1.0, max(0.0, value / total)), 7)
