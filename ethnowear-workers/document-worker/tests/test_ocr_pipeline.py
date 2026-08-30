import asyncio
from pathlib import Path

import cv2
import numpy as np

from ethnowear_document_worker.layout.detector import LayoutAnalysis
from ethnowear_document_worker.layout.models import LayoutRegion
from ethnowear_document_worker.ocr.models import BoundingBox, OcrAttempt, OcrBlockType, OcrOutput, OcrWord
from ethnowear_document_worker.ocr.pipeline import OcrPipeline, OcrPipelineError, _select_attempt, _selection_key


def recognized(text: str, confidence: float, *, offset_x: int = 0, offset_y: int = 0) -> OcrOutput:
    words = tuple(
        OcrWord(1, 1, 1, 1, index, offset_x + index * 20, offset_y, 18, 20, confidence, token)
        for index, token in enumerate(text.split(), start=1)
    )
    return OcrOutput(text, confidence, words, "tesseract", "5.5.0", "bul")


class FakeTesseract:
    language = "bul"

    def __init__(self) -> None:
        self.calls: list[tuple[str, int]] = []

    async def engine_version(self) -> str:
        return "5.5.0"

    async def recognize(self, path: Path, maximum_output_bytes: int, *, psm: int, offset_x: int = 0, offset_y: int = 0) -> OcrOutput:
        self.calls.append((path.name, psm))
        if path.name == "region-2.png":
            return recognized("Надпис към фигурата", 0.96, offset_x=offset_x, offset_y=offset_y)
        if path.name == "region-3.png":
            return recognized("Долен български параграф с четлив текст", 0.95, offset_x=offset_x, offset_y=offset_y)
        return recognized("xЖ?? ####", 0.40)


def test_pipeline_excludes_illustration_and_selects_ordered_region_text(
    tmp_path: Path,
    monkeypatch,
) -> None:
    source = tmp_path / "page.png"
    cv2.imwrite(str(source), np.full((500, 400), 255, dtype=np.uint8))
    layout = LayoutAnalysis(400, 500, (
        LayoutRegion(1, OcrBlockType.ILLUSTRATION, BoundingBox(50, 80, 300, 200), 0),
        LayoutRegion(2, OcrBlockType.CAPTION, BoundingBox(60, 300, 280, 40), 1),
        LayoutRegion(3, OcrBlockType.BODY_TEXT, BoundingBox(30, 360, 340, 100), 2),
    ))
    monkeypatch.setattr("ethnowear_document_worker.ocr.pipeline.detect_layout", lambda *args, **kwargs: layout)
    runner = FakeTesseract()

    output = asyncio.run(OcrPipeline(runner).recognize_page(
        source,
        tmp_path,
        100_000,
        maximum_figure_candidates=10,
        maximum_figure_caption_characters=2_000,
    ))

    assert output.strategy == "REGION_PSM_6"
    assert output.raw_text == (
        "Надпис към фигурата\n\n"
        "Долен български параграф с четлив текст"
    )
    assert [block.block_type for block in output.blocks] == [
        OcrBlockType.ILLUSTRATION,
        OcrBlockType.CAPTION,
        OcrBlockType.BODY_TEXT,
    ]
    assert ("region-1.png", 6) not in runner.calls
    assert len(output.attempts) == 3
    assert len(output.figure_candidates) == 1
    assert output.figure_candidates[0].raw_caption_text == "Надпис към фигурата"
    assert {attempt.psm for attempt in output.attempts} == {3, 4, 6}
    assert "\t" not in output.raw_text
    assert any(
        block.block_type is OcrBlockType.ILLUSTRATION
        for block in output.blocks
    )
    assert all(
        block.text
        for block in output.blocks
        if block.block_type is not OcrBlockType.ILLUSTRATION
    )


class StructuredArtifactTesseract(FakeTesseract):
    async def recognize(self, path: Path, maximum_output_bytes: int, *, psm: int, offset_x: int = 0, offset_y: int = 0) -> OcrOutput:
        if path.name == "region-2.png":
            return recognized(
                "5\t1\t13\t1\t1\t2\t719\t1604\t34\t27\t93.0\nНадпис",
                0.90,
                offset_x=offset_x,
                offset_y=offset_y,
            )
        return await super().recognize(
            path,
            maximum_output_bytes,
            psm=psm,
            offset_x=offset_x,
            offset_y=offset_y,
        )


def test_pipeline_removes_structured_row_artifacts_without_failing_page(
    tmp_path: Path,
    monkeypatch,
) -> None:
    source = tmp_path / "page.png"
    cv2.imwrite(str(source), np.full((500, 400), 255, dtype=np.uint8))
    layout = LayoutAnalysis(400, 500, (
        LayoutRegion(2, OcrBlockType.CAPTION, BoundingBox(60, 300, 280, 40), 0),
        LayoutRegion(3, OcrBlockType.BODY_TEXT, BoundingBox(30, 360, 340, 100), 1),
    ))
    monkeypatch.setattr("ethnowear_document_worker.ocr.pipeline.detect_layout", lambda *args, **kwargs: layout)

    output = asyncio.run(OcrPipeline(StructuredArtifactTesseract()).recognize_page(
        source,
        tmp_path,
        100_000,
        maximum_figure_candidates=10,
        maximum_figure_caption_characters=2_000,
    ))

    assert "719\t1604" not in output.raw_text
    assert "Надпис" in output.raw_text
    assert "STRUCTURED_ROW_ARTIFACTS_REMOVED" in output.warnings


class OneStrategyFailsTesseract(FakeTesseract):
    async def recognize(self, path: Path, maximum_output_bytes: int, *, psm: int, offset_x: int = 0, offset_y: int = 0) -> OcrOutput:
        if path.name == "masked.png" and psm == 3:
            raise OcrPipelineError("bad layout artifact")
        return await super().recognize(
            path,
            maximum_output_bytes,
            psm=psm,
            offset_x=offset_x,
            offset_y=offset_y,
        )


def test_pipeline_keeps_successful_attempts_when_one_layout_strategy_fails(
    tmp_path: Path,
    monkeypatch,
) -> None:
    source = tmp_path / "page.png"
    cv2.imwrite(str(source), np.full((500, 400), 255, dtype=np.uint8))
    layout = LayoutAnalysis(400, 500, (
        LayoutRegion(3, OcrBlockType.BODY_TEXT, BoundingBox(30, 360, 340, 100), 0),
    ))
    monkeypatch.setattr("ethnowear_document_worker.ocr.pipeline.detect_layout", lambda *args, **kwargs: layout)

    output = asyncio.run(OcrPipeline(OneStrategyFailsTesseract()).recognize_page(
        source,
        tmp_path,
        100_000,
        maximum_figure_candidates=10,
        maximum_figure_caption_characters=2_000,
    ))

    assert len(output.attempts) == 2
    assert {attempt.psm for attempt in output.attempts} == {4, 6}


def test_candidate_failure_does_not_fail_ocr(
        tmp_path: Path,
        monkeypatch,
) -> None:
    source = tmp_path / "page.png"
    cv2.imwrite(str(source), np.full((500, 400), 255, dtype=np.uint8))
    layout = LayoutAnalysis(400, 500, (
        LayoutRegion(
            1,
            OcrBlockType.BODY_TEXT,
            BoundingBox(30, 50, 340, 300),
            0,
        ),
    ))
    monkeypatch.setattr(
        "ethnowear_document_worker.ocr.pipeline.detect_layout",
        lambda *args, **kwargs: layout,
    )

    def fail_candidates(*args, **kwargs):
        raise ValueError("candidate failure")

    monkeypatch.setattr(
        "ethnowear_document_worker.ocr.pipeline.build_figure_candidates",
        fail_candidates,
    )

    output = asyncio.run(OcrPipeline(FakeTesseract()).recognize_page(
        source,
        tmp_path,
        100_000,
        maximum_figure_candidates=10,
        maximum_figure_caption_characters=2_000,
    ))

    assert output.raw_text
    assert output.figure_candidates == ()
    assert "FIGURE_CANDIDATE_DETECTION_FAILED" in output.warnings


def test_clean_attempt_is_selected_over_higher_scoring_garbage() -> None:
    clean = OcrAttempt(
        strategy="MASKED_PAGE_PSM_3", psm=3, preprocessing=(),
        raw_text="Четлив български и Latin text", mean_confidence=0.82,
        score=0.78, warnings=(), blocks=(), words=(),
    )
    garbage = OcrAttempt(
        strategy="REGION_PSM_6", psm=6, preprocessing=(),
        raw_text="------5353Алалооаааав", mean_confidence=0.94,
        score=0.91, warnings=("GARBAGE_SEQUENCE",), blocks=(), words=(),
    )

    assert max((clean, garbage), key=_selection_key) is clean


def test_region_attempt_requires_material_advantage_over_clean_page() -> None:
    page = OcrAttempt(
        strategy="MASKED_PAGE_PSM_3", psm=3, preprocessing=(),
        raw_text="Paragraphs in native reading order", mean_confidence=0.88,
        score=0.79, warnings=("LOW_CONFIDENCE_REGION",), blocks=(), words=(),
    )
    slightly_higher_region = OcrAttempt(
        strategy="REGION_PSM_6", psm=6, preprocessing=(),
        raw_text="Fragmented region output", mean_confidence=0.94,
        score=0.88, warnings=("LOW_CONFIDENCE_REGION",), blocks=(), words=(),
    )

    assert _select_attempt([page, slightly_higher_region]) is page


def test_region_attempt_can_replace_materially_worse_page() -> None:
    page = OcrAttempt(
        strategy="MASKED_PAGE_PSM_3", psm=3, preprocessing=(),
        raw_text="Poor page", mean_confidence=0.45,
        score=0.40, warnings=("LOW_CONFIDENCE_WORDS",), blocks=(), words=(),
    )
    region = OcrAttempt(
        strategy="REGION_PSM_6", psm=6, preprocessing=(),
        raw_text="Recovered text", mean_confidence=0.91,
        score=0.75, warnings=(), blocks=(), words=(),
    )

    assert _select_attempt([page, region]) is region


def test_psm_three_wins_close_full_page_score_to_preserve_reading_order() -> None:
    psm_three = OcrAttempt(
        strategy="MASKED_PAGE_PSM_3", psm=3, preprocessing=(),
        raw_text="Continuous paragraph", mean_confidence=0.90,
        score=0.79, warnings=(), blocks=(), words=(),
    )
    psm_four = OcrAttempt(
        strategy="MASKED_PAGE_PSM_4", psm=4, preprocessing=(),
        raw_text="Interleaved columns", mean_confidence=0.92,
        score=0.80, warnings=(), blocks=(), words=(),
    )

    assert _select_attempt([psm_three, psm_four]) is psm_three
