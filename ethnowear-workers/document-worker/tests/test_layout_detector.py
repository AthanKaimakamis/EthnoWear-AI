from pathlib import Path

import cv2
import numpy as np

from ethnowear_document_worker.layout.detector import (
    LayoutAnalysis,
    _reading_order,
    create_masked_page,
    crop_region,
    detect_layout,
)
from ethnowear_document_worker.layout.models import LayoutRegion
from ethnowear_document_worker.ocr.models import BoundingBox, OcrBlockType


def region(
    region_id: int,
    region_type: OcrBlockType,
    left: int,
    top: int,
    width: int,
    height: int,
) -> LayoutRegion:
    return LayoutRegion(region_id, region_type, BoundingBox(left, top, width, height))


def test_reading_order_keeps_caption_after_illustration_and_before_lower_text() -> None:
    ordered = _reading_order([
        region(1, OcrBlockType.HEADER, 50, 10, 900, 40),
        region(2, OcrBlockType.BODY_TEXT, 50, 80, 900, 100),
        region(3, OcrBlockType.ILLUSTRATION, 100, 220, 800, 400),
        region(4, OcrBlockType.CAPTION, 250, 640, 500, 40),
        region(5, OcrBlockType.BODY_TEXT, 50, 710, 900, 150),
        region(6, OcrBlockType.PAGE_NUMBER, 900, 10, 40, 40),
    ], 1_000)

    assert [item.region_id for item in ordered] == [1, 6, 2, 3, 4, 5]
    assert [item.reading_order for item in ordered] == list(range(6))


def test_reading_order_processes_columns_left_then_right() -> None:
    ordered = _reading_order([
        region(1, OcrBlockType.BODY_TEXT, 50, 100, 400, 100),
        region(2, OcrBlockType.BODY_TEXT, 550, 80, 400, 100),
        region(3, OcrBlockType.BODY_TEXT, 50, 240, 400, 100),
        region(4, OcrBlockType.BODY_TEXT, 550, 220, 400, 100),
    ], 1_000)

    assert [item.region_id for item in ordered] == [1, 3, 2, 4]


def test_masking_whitens_illustration_and_crop_is_bounded(tmp_path: Path) -> None:
    source = tmp_path / "page.png"
    masked = tmp_path / "masked.png"
    cropped = tmp_path / "crop.png"
    image = np.full((200, 300, 3), 255, dtype=np.uint8)
    image[60:150, 80:220] = 0
    cv2.imwrite(str(source), image)
    bounds = BoundingBox(80, 60, 140, 90)
    layout = LayoutAnalysis(
        300,
        200,
        (LayoutRegion(1, OcrBlockType.ILLUSTRATION, bounds),),
    )

    create_masked_page(source, masked, layout)
    crop_region(source, cropped, BoundingBox(10, 20, 50, 40))

    result = cv2.imread(str(masked), cv2.IMREAD_GRAYSCALE)
    crop = cv2.imread(str(cropped), cv2.IMREAD_GRAYSCALE)
    assert result is not None and np.all(result[60:150, 80:220] == 255)
    assert crop is not None and crop.shape == (40, 50)


def test_detect_layout_separates_dense_central_illustration(tmp_path: Path) -> None:
    source = tmp_path / "illustrated-page.png"
    image = np.full((1_200, 800), 245, dtype=np.uint8)
    for top in range(80, 300, 45):
        cv2.putText(image, "Bulgarian text line", (80, top), cv2.FONT_HERSHEY_SIMPLEX, 0.7, 20, 2)
    random = np.random.default_rng(42)
    image[360:760, 140:660] = random.integers(0, 256, (400, 520), dtype=np.uint8)
    cv2.putText(image, "Caption", (300, 810), cv2.FONT_HERSHEY_SIMPLEX, 0.7, 20, 2)
    cv2.imwrite(str(source), image)

    layout = detect_layout(source)

    assert len(layout.illustration_regions) == 1
    illustration = layout.illustration_regions[0].bounds
    assert illustration.top < 400
    assert illustration.bottom > 700
    assert any(region.region_type is OcrBlockType.CAPTION for region in layout.text_regions)


def test_detect_layout_finds_sparse_line_illustration(tmp_path: Path) -> None:
    source = tmp_path / "sparse-line-art.png"
    image = np.full((1_200, 800), 245, dtype=np.uint8)
    for top in range(80, 260, 42):
        cv2.putText(image, "Printed text line", (90, top), cv2.FONT_HERSHEY_SIMPLEX, 0.65, 20, 2)
    for offset in range(0, 420, 70):
        points = np.array([
            [180 + offset, 460],
            [215 + offset, 390],
            [250 + offset, 460],
            [215 + offset, 530],
        ])
        cv2.polylines(image, [points], True, 15, 4)
    cv2.line(image, (150, 560), (650, 560), 15, 4)
    cv2.putText(image, "Fig. 12 - Ornament", (260, 610), cv2.FONT_HERSHEY_SIMPLEX, 0.55, 20, 2)
    cv2.imwrite(str(source), image)

    layout = detect_layout(source)

    assert len(layout.illustration_regions) == 1
    illustration = layout.illustration_regions[0].bounds
    assert illustration.top < 470
    assert illustration.bottom > 530


def test_detect_layout_keeps_multiple_sparse_figures_separate(tmp_path: Path) -> None:
    source = tmp_path / "multiple-line-art.png"
    image = np.full((1_400, 900), 245, dtype=np.uint8)
    for top in (250, 750):
        for offset in range(0, 480, 80):
            cv2.rectangle(image, (190 + offset, top), (245 + offset, top + 150), 15, 4)
            cv2.line(image, (190 + offset, top), (245 + offset, top + 150), 15, 4)
        cv2.line(image, (180, top + 150), (660, top + 150), 15, 4)
        cv2.putText(image, "Figure caption", (320, top + 205), cv2.FONT_HERSHEY_SIMPLEX, 0.55, 20, 2)
    cv2.imwrite(str(source), image)

    layout = detect_layout(source)

    assert len(layout.illustration_regions) == 2


def test_detect_layout_does_not_treat_text_only_page_as_illustration(tmp_path: Path) -> None:
    source = tmp_path / "text-only.png"
    image = np.full((1_200, 800), 245, dtype=np.uint8)
    for top in range(90, 1_080, 38):
        cv2.putText(
            image,
            "This is an ordinary printed paragraph line.",
            (70, top),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.55,
            20,
            1,
        )
    cv2.imwrite(str(source), image)

    layout = detect_layout(source)

    assert layout.illustration_regions == ()
