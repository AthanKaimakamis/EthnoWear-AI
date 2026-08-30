from dataclasses import dataclass

from ethnowear_document_worker.ocr.models import BoundingBox, OcrBlockType


@dataclass(frozen=True, slots=True)
class LayoutRegion:
    region_id: int
    region_type: OcrBlockType
    bounds: BoundingBox
    reading_order: int = 0
    detection_confidence: float | None = None


def intersection_ratio(first: BoundingBox, second: BoundingBox) -> float:
    width = max(0, min(first.right, second.right) - max(first.left, second.left))
    height = max(0, min(first.bottom, second.bottom) - max(first.top, second.top))
    intersection = width * height
    return intersection / first.area if first.area else 0.0
