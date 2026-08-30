from dataclasses import dataclass, replace
from pathlib import Path

import cv2
import numpy as np

from ethnowear_document_worker.layout.models import LayoutRegion, intersection_ratio
from ethnowear_document_worker.ocr.models import BoundingBox, OcrBlockType


class LayoutDetectionError(ValueError):
    pass


@dataclass(frozen=True, slots=True)
class LayoutAnalysis:
    width: int
    height: int
    regions: tuple[LayoutRegion, ...]

    @property
    def illustration_regions(self) -> tuple[LayoutRegion, ...]:
        return tuple(
            region for region in self.regions
            if region.region_type is OcrBlockType.ILLUSTRATION
        )

    @property
    def text_regions(self) -> tuple[LayoutRegion, ...]:
        return tuple(
            region for region in self.regions
            if region.region_type is not OcrBlockType.ILLUSTRATION
        )


def detect_layout(path: Path, *, maximum_regions: int = 32) -> LayoutAnalysis:
    if maximum_regions <= 0:
        raise ValueError("Maximum layout regions must be positive")
    gray = cv2.imread(str(path), cv2.IMREAD_GRAYSCALE)
    if gray is None:
        raise LayoutDetectionError("OCR layout detection failed")
    height, width = gray.shape
    binary = cv2.threshold(
        gray,
        0,
        255,
        cv2.THRESH_BINARY_INV | cv2.THRESH_OTSU,
    )[1]
    border_y = max(1, int(height * 0.015))
    _clear_scanner_borders(binary)
    binary[:border_y, :] = 0
    binary[height - border_y:, :] = 0

    illustrations = _illustrations(gray, binary)
    text_binary = binary.copy()
    for bounds in illustrations:
        padding = max(3, min(width, height) // 300)
        cv2.rectangle(
            text_binary,
            (max(0, bounds.left - padding), max(0, bounds.top - padding)),
            (min(width, bounds.right + padding), min(height, bounds.bottom + padding)),
            0,
            thickness=-1,
        )

    text_boxes = _text_blocks(text_binary, width, height)
    text_boxes = [
        box for box in text_boxes
        if not any(intersection_ratio(box, image) > 0.15 for image in illustrations)
    ]
    regions = [
        LayoutRegion(
            index + 1,
            OcrBlockType.ILLUSTRATION,
            box,
            detection_confidence=_illustration_confidence(
                gray,
                binary,
                box,
            ),
        )
        for index, box in enumerate(illustrations)
    ]
    next_id = len(regions) + 1
    median_height = float(np.median([box.height for box in text_boxes])) if text_boxes else 0.0
    for box in text_boxes:
        region_type = _classify_text_region(
            box,
            width,
            height,
            median_height,
            illustrations,
        )
        regions.append(LayoutRegion(next_id, region_type, box))
        next_id += 1

    if not text_boxes:
        margin_x = max(1, width // 50)
        margin_y = max(1, height // 50)
        regions.append(LayoutRegion(
            next_id,
            OcrBlockType.UNKNOWN_TEXT,
            BoundingBox(margin_x, margin_y, width - 2 * margin_x, height - 2 * margin_y),
        ))

    ordered = _reading_order(regions, width)
    if len(ordered) > maximum_regions:
        raise LayoutDetectionError("OCR layout contains too many regions")
    return LayoutAnalysis(width, height, tuple(ordered))


def _clear_scanner_borders(binary: np.ndarray) -> None:
    height, width = binary.shape
    density = np.count_nonzero(binary, axis=0) / height
    search_width = max(1, int(width * 0.15))
    padding = max(3, width // 100)
    left_dense = np.flatnonzero(density[:search_width] >= 0.35)
    right_dense = np.flatnonzero(density[width - search_width:] >= 0.35)
    left = int(left_dense.max()) + padding if left_dense.size else max(1, width // 100)
    right = (
        width - search_width + int(right_dense.min()) - padding
        if right_dense.size else width - max(1, width // 100)
    )
    binary[:, :max(0, left)] = 0
    binary[:, min(width, right):] = 0


def create_masked_page(
        source: Path,
        destination: Path,
        layout: LayoutAnalysis,
) -> None:
    image = cv2.imread(str(source), cv2.IMREAD_COLOR)
    if image is None:
        raise LayoutDetectionError("OCR illustration masking failed")
    for region in layout.illustration_regions:
        box = region.bounds
        cv2.rectangle(
            image,
            (box.left, box.top),
            (box.right, box.bottom),
            (255, 255, 255),
            thickness=-1,
        )
    if not cv2.imwrite(str(destination), image):
        raise LayoutDetectionError("OCR illustration masking failed")
    destination.chmod(0o600)


def crop_region(source: Path, destination: Path, bounds: BoundingBox) -> None:
    image = cv2.imread(str(source), cv2.IMREAD_GRAYSCALE)
    if image is None:
        raise LayoutDetectionError("OCR region crop failed")
    crop = image[bounds.top:bounds.bottom, bounds.left:bounds.right]
    if crop.size == 0 or not cv2.imwrite(str(destination), crop):
        raise LayoutDetectionError("OCR region crop failed")
    destination.chmod(0o600)


def _illustrations(gray: np.ndarray, binary: np.ndarray) -> list[BoundingBox]:
    dense_candidates = _dense_illustrations(gray, binary)
    sparse_candidates = _sparse_line_illustrations(gray, binary)
    candidates = _remove_contained(sorted(
        dense_candidates + sparse_candidates,
        key=lambda box: box.area,
        reverse=True,
    ))
    return _merge_illustration_parts(candidates, binary)


def _dense_illustrations(gray: np.ndarray, binary: np.ndarray) -> list[BoundingBox]:
    height, width = gray.shape
    density = cv2.blur(
        (binary > 0).astype(np.float32),
        (max(21, width // 50), max(21, height // 70)),
    )
    dense_regions = (density > 0.18).astype(np.uint8) * 255
    closed = cv2.morphologyEx(
        dense_regions,
        cv2.MORPH_CLOSE,
        cv2.getStructuringElement(cv2.MORPH_RECT, (
            max(5, width // 100),
            max(5, height // 150),
        )),
        iterations=2,
    )
    contours, _ = cv2.findContours(closed, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    page_area = width * height
    candidates: list[BoundingBox] = []
    for contour in contours:
        x, y, candidate_width, candidate_height = cv2.boundingRect(contour)
        area_ratio = candidate_width * candidate_height / page_area
        if area_ratio < 0.025 or candidate_width < width * 0.20 or candidate_height < height * 0.08:
            continue
        patch = binary[y:y + candidate_height, x:x + candidate_width]
        ink_ratio = float(np.count_nonzero(patch)) / patch.size
        gray_patch = gray[y:y + candidate_height, x:x + candidate_width]
        texture = float(np.std(gray_patch))
        touches_vertical_edge = x <= width * 0.01 or x + candidate_width >= width * 0.99
        if ink_ratio >= 0.25 and texture >= 35.0 and not touches_vertical_edge:
            candidates.append(BoundingBox(x, y, candidate_width, candidate_height))
    return candidates


def _sparse_line_illustrations(
        gray: np.ndarray,
        binary: np.ndarray,
) -> list[BoundingBox]:
    """Detect large coherent line art that has too much white space for the dense path."""
    height, width = gray.shape
    page_area = width * height
    connected = cv2.morphologyEx(
        binary,
        cv2.MORPH_CLOSE,
        cv2.getStructuringElement(cv2.MORPH_RECT, (
            max(5, width // 180),
            max(5, height // 220),
        )),
        iterations=2,
    )
    connected = cv2.dilate(
        connected,
        cv2.getStructuringElement(cv2.MORPH_RECT, (
            max(3, width // 250),
            max(3, height // 300),
        )),
        iterations=1,
    )
    contours, _ = cv2.findContours(
        connected,
        cv2.RETR_EXTERNAL,
        cv2.CHAIN_APPROX_SIMPLE,
    )
    candidates: list[BoundingBox] = []
    for contour in contours:
        x, y, candidate_width, candidate_height = cv2.boundingRect(contour)
        area_ratio = candidate_width * candidate_height / page_area
        if (
            area_ratio < 0.015
            or candidate_width < width * 0.20
            or candidate_height < height * 0.055
        ):
            continue

        patch = binary[y:y + candidate_height, x:x + candidate_width]
        gray_patch = gray[y:y + candidate_height, x:x + candidate_width]
        ink_ratio = float(np.count_nonzero(patch)) / patch.size
        texture = float(np.std(gray_patch))
        touches_vertical_edge = x <= width * 0.01 or x + candidate_width >= width * 0.99

        # Text paragraphs have large bounding boxes after morphology, but retain
        # substantially less ink and texture than coherent drawings.
        if ink_ratio >= 0.14 and texture >= 28.0 and not touches_vertical_edge:
            candidates.append(BoundingBox(x, y, candidate_width, candidate_height))
    return candidates


def _merge_illustration_parts(
        boxes: list[BoundingBox],
        binary: np.ndarray,
) -> list[BoundingBox]:
    """Join vertically adjacent artwork bands when no caption separates them."""
    if not boxes:
        return []

    height, _ = binary.shape
    merged: list[BoundingBox] = []
    for box in sorted(boxes, key=lambda item: (item.top, item.left)):
        if not merged:
            merged.append(box)
            continue

        previous = merged[-1]
        overlap = max(0, min(previous.right, box.right) - max(previous.left, box.left))
        minimum_width = min(previous.width, box.width)
        gap = box.top - previous.bottom
        if (
            minimum_width > 0
            and overlap / minimum_width >= 0.35
            and 0 <= gap <= height * 0.08
            and not _has_text_separator(binary, previous, box)
        ):
            left = min(previous.left, box.left)
            top = previous.top
            right = max(previous.right, box.right)
            bottom = max(previous.bottom, box.bottom)
            merged[-1] = BoundingBox(left, top, right - left, bottom - top)
        else:
            merged.append(box)
    return merged


def _has_text_separator(
        binary: np.ndarray,
        upper: BoundingBox,
        lower: BoundingBox,
) -> bool:
    left = max(upper.left, lower.left)
    right = min(upper.right, lower.right)
    gap = binary[upper.bottom:lower.top, left:right]
    if gap.size == 0 or gap.shape[1] == 0:
        return False
    maximum_row_ink = float(np.max(np.count_nonzero(gap, axis=1) / gap.shape[1]))
    return maximum_row_ink >= 0.05


def _illustration_confidence(
        gray: np.ndarray,
        binary: np.ndarray,
        bounds: BoundingBox,
) -> float:
    patch = binary[bounds.top:bounds.bottom, bounds.left:bounds.right]
    gray_patch = gray[bounds.top:bounds.bottom, bounds.left:bounds.right]

    if patch.size == 0 or gray_patch.size == 0:
        return 0.0

    page_area = gray.shape[0] * gray.shape[1]
    area_ratio = bounds.area / page_area
    ink_ratio = float(np.count_nonzero(patch)) / patch.size
    texture = float(np.std(gray_patch))

    ink_score = _bounded_ratio(ink_ratio, 0.20, 0.55)
    texture_score = _bounded_ratio(texture, 25.0, 75.0)
    area_score = _bounded_ratio(area_ratio, 0.025, 0.25)

    return round(
        min(1.0, 0.45 + ink_score * 0.25 + texture_score * 0.20 + area_score * 0.10),
        4,
    )


def _bounded_ratio(value: float, minimum: float, maximum: float) -> float:
    if maximum <= minimum:
        raise ValueError("Confidence range must be increasing")

    return min(1.0, max(0.0, (value - minimum) / (maximum - minimum)))


def _text_blocks(binary: np.ndarray, width: int, height: int) -> list[BoundingBox]:
    line_kernel = cv2.getStructuringElement(
        cv2.MORPH_RECT,
        (max(15, width // 70), max(2, height // 1500)),
    )
    lines = cv2.dilate(binary, line_kernel, iterations=1)
    contours, _ = cv2.findContours(lines, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    boxes = []
    for contour in contours:
        x, y, box_width, box_height = cv2.boundingRect(contour)
        if box_width < width * 0.025 or box_height < 4:
            continue
        if box_width * box_height < width * height * 0.00004:
            continue
        boxes.append(BoundingBox(x, y, box_width, box_height))
    return _merge_lines(boxes, width, height)


def _merge_lines(
        boxes: list[BoundingBox],
        width: int,
        height: int,
) -> list[BoundingBox]:
    if not boxes:
        return []
    boxes = sorted(boxes, key=lambda box: (box.top, box.left))
    median_height = float(np.median([box.height for box in boxes]))
    blocks: list[BoundingBox] = []
    for box in boxes:
        match = None
        for index, block in enumerate(blocks):
            gap = box.top - block.bottom
            overlap = max(0, min(box.right, block.right) - max(box.left, block.left))
            overlap_ratio = overlap / min(box.width, block.width)
            similar_column = overlap_ratio >= 0.35 or abs(box.left - block.left) <= width * 0.035
            if -median_height <= gap <= median_height * 1.25 and similar_column:
                match = index
                break
        if match is None:
            blocks.append(box)
            continue
        block = blocks[match]
        left = min(block.left, box.left)
        top = min(block.top, box.top)
        right = max(block.right, box.right)
        bottom = max(block.bottom, box.bottom)
        blocks[match] = BoundingBox(left, top, right - left, bottom - top)

    padding_x = max(3, width // 300)
    padding_y = max(2, height // 500)
    padded = [
        BoundingBox(
            max(0, box.left - padding_x),
            max(0, box.top - padding_y),
            min(width, box.right + padding_x) - max(0, box.left - padding_x),
            min(height, box.bottom + padding_y) - max(0, box.top - padding_y),
        )
        for box in blocks
    ]
    return _remove_contained(sorted(padded, key=lambda box: box.area, reverse=True))


def _classify_text_region(
        box: BoundingBox,
        width: int,
        height: int,
        median_height: float,
        illustrations: list[BoundingBox],
) -> OcrBlockType:
    if box.top < height * 0.12 and box.height < height * 0.06:
        if box.width < width * 0.18:
            return OcrBlockType.PAGE_NUMBER
        return OcrBlockType.HEADER
    for image in illustrations:
        horizontal_overlap = max(0, min(box.right, image.right) - max(box.left, image.left))
        distance_below = box.top - image.bottom
        if (
            horizontal_overlap >= min(box.width, image.width) * 0.35
            and 0 <= distance_below <= height * 0.06
            and box.height <= height * 0.08
        ):
            return OcrBlockType.CAPTION
    if box.top > height * 0.78 and median_height and box.height < median_height * 0.85:
        return OcrBlockType.FOOTNOTE
    return OcrBlockType.BODY_TEXT


def _reading_order(regions: list[LayoutRegion], page_width: int) -> list[LayoutRegion]:
    illustrations = [region for region in regions if region.region_type is OcrBlockType.ILLUSTRATION]
    text = [region for region in regions if region.region_type is not OcrBlockType.ILLUSTRATION]
    full_width = [region for region in text if region.bounds.width >= page_width * 0.70]
    columns = [region for region in text if region not in full_width]
    anchors = sorted(
        full_width + illustrations,
        key=lambda item: (item.bounds.top, item.bounds.left),
    )
    ordered: list[LayoutRegion] = []
    segment_top = 0
    consumed: set[int] = set()
    for anchor in anchors:
        segment = [
            region for region in columns
            if region.region_id not in consumed
            and segment_top <= region.bounds.top
            and region.bounds.bottom <= anchor.bounds.top
        ]
        ordered.extend(_order_columns(segment))
        consumed.update(region.region_id for region in segment)
        ordered.append(anchor)
        overlapping = [
            region for region in columns
            if region.region_id not in consumed
            and region.bounds.top < anchor.bounds.bottom
        ]
        ordered.extend(_order_columns(overlapping))
        consumed.update(region.region_id for region in overlapping)
        segment_top = max(segment_top, anchor.bounds.bottom)
    ordered.extend(_order_columns([
        region for region in columns
        if region.region_id not in consumed
    ]))
    return [replace(region, reading_order=index) for index, region in enumerate(ordered)]


def _same_column(first: LayoutRegion, second: LayoutRegion) -> bool:
    overlap = max(0, min(first.bounds.right, second.bounds.right) - max(first.bounds.left, second.bounds.left))
    return overlap >= min(first.bounds.width, second.bounds.width) * 0.30


def _order_columns(regions: list[LayoutRegion]) -> list[LayoutRegion]:
    groups: list[list[LayoutRegion]] = []
    for region in sorted(regions, key=lambda item: item.bounds.left):
        group = next((candidate for candidate in groups if _same_column(candidate[0], region)), None)
        if group is None:
            groups.append([region])
        else:
            group.append(region)
    return [
        region
        for group in groups
        for region in sorted(group, key=lambda item: (item.bounds.top, item.bounds.left))
    ]


def _remove_contained(boxes: list[BoundingBox]) -> list[BoundingBox]:
    result: list[BoundingBox] = []
    for box in boxes:
        if any(intersection_ratio(box, existing) > 0.85 for existing in result):
            continue
        result.append(box)
    return result
