import re
import statistics
import unicodedata
from dataclasses import replace
from pathlib import Path

from ethnowear_document_worker.layout.detector import (
    LayoutAnalysis,
    create_masked_page,
    crop_region,
    detect_layout,
)
from ethnowear_document_worker.layout.preprocessing import preprocess_if_beneficial
from ethnowear_document_worker.ocr.candidates import build_figure_candidates
from ethnowear_document_worker.ocr.models import (
    OcrAttempt,
    OcrBlock,
    OcrBlockType,
    OcrOutput,
    OcrWord,
)
from ethnowear_document_worker.ocr.tesseract import TesseractRunner

_CYRILLIC = re.compile(r"[\u0400-\u052f]")
_LATIN = re.compile(r"[A-Za-z]")
_TSV_ROW = re.compile(r"^\d(?:\t[^\t]*){10,}$", re.MULTILINE)
_SEVERE_SELECTION_WARNINGS = frozenset({
    "STRUCTURED_ROW_ARTIFACTS",
    "GARBAGE_SEQUENCE",
})


class OcrPipelineError(ValueError):
    pass


class OcrPipeline:
    def __init__(self, tesseract: TesseractRunner, *, maximum_regions: int = 32) -> None:
        self._tesseract = tesseract
        self._maximum_regions = maximum_regions

    async def recognize_page(
            self,
            image_path: Path,
            workspace_path: Path,
            maximum_output_bytes: int,
            *,
            maximum_figure_candidates: int,
            maximum_figure_caption_characters: int,
    ) -> OcrOutput:
        preprocessing = preprocess_if_beneficial(
            image_path,
            workspace_path / "enhanced.png",
        )
        layout = detect_layout(
            preprocessing.path,
            maximum_regions=self._maximum_regions,
        )
        masked = workspace_path / "masked.png"
        create_masked_page(preprocessing.path, masked, layout)

        attempts: list[OcrAttempt] = []
        failures: list[OcrPipelineError] = []
        for psm in (3, 4):
            try:
                attempts.append(await self._full_page_attempt(
                    masked,
                    layout,
                    preprocessing.steps,
                    psm,
                    maximum_output_bytes,
                ))
            except OcrPipelineError as error:
                failures.append(error)
        try:
            attempts.append(await self._region_attempt(
                preprocessing.path,
                workspace_path,
                layout,
                preprocessing.steps,
                maximum_output_bytes,
            ))
        except OcrPipelineError as error:
            failures.append(error)
        if not attempts:
            raise OcrPipelineError("All OCR layout strategies failed") from failures[-1]
        selected = _select_attempt(attempts)
        warnings = set(selected.warnings)
        try:
            figure_candidates = build_figure_candidates(
                layout,
                selected.blocks,
                maximum_candidates=maximum_figure_candidates,
                maximum_caption_characters=(
                    maximum_figure_caption_characters
                ),
            )
        except Exception:
            # Figure discovery is advisory and must never fail successful OCR.
            figure_candidates = ()
            warnings.add("FIGURE_CANDIDATE_DETECTION_FAILED")

        return OcrOutput(
            raw_text=selected.raw_text,
            mean_confidence=selected.mean_confidence,
            words=selected.words,
            engine_name="tesseract",
            engine_version=await self._tesseract.engine_version(),
            language=self._tesseract.language,
            psm=selected.psm,
            strategy=selected.strategy,
            preprocessing=selected.preprocessing,
            warnings=tuple(sorted(warnings)),
            blocks=selected.blocks,
            attempts=tuple(attempts),
            figure_candidates=figure_candidates,
        )

    async def _full_page_attempt(
            self,
            image_path: Path,
            layout: LayoutAnalysis,
            preprocessing: tuple[str, ...],
            psm: int,
            maximum_output_bytes: int,
    ) -> OcrAttempt:
        output = await self._tesseract.recognize(
            image_path,
            maximum_output_bytes,
            psm=psm,
        )
        blocks = _blocks_from_words(output.words, layout)
        # Tesseract's plain output owns transcription and reading order. TSV
        # words are retained only for confidence, coordinates, and block metadata.
        raw_text = output.raw_text
        warnings = _warnings(raw_text, output.words, blocks, layout)
        return OcrAttempt(
            strategy=f"MASKED_PAGE_PSM_{psm}",
            psm=psm,
            preprocessing=preprocessing,
            raw_text=raw_text,
            mean_confidence=output.mean_confidence,
            score=_score(raw_text, output.words, warnings, region_strategy=False),
            warnings=warnings,
            blocks=blocks,
            words=output.words,
        )

    async def _region_attempt(
            self,
            image_path: Path,
            workspace_path: Path,
            layout: LayoutAnalysis,
            preprocessing: tuple[str, ...],
            maximum_output_bytes: int,
    ) -> OcrAttempt:
        blocks: list[OcrBlock] = [_illustration_block(region) for region in layout.illustration_regions]
        all_words: list[OcrWord] = []
        removed_structured_rows = False
        for region in layout.text_regions:
            crop_path = workspace_path / f"region-{region.region_id}.png"
            crop_region(image_path, crop_path, region.bounds)
            output = await self._tesseract.recognize(
                crop_path,
                maximum_output_bytes,
                psm=6,
                offset_x=region.bounds.left,
                offset_y=region.bounds.top,
            )
            clean_text, removed_from_region = _remove_structured_rows(output.raw_text)
            removed_structured_rows = removed_structured_rows or removed_from_region
            block_type = _refine_type(region.region_type, clean_text)
            block = OcrBlock(
                block_id=region.region_id,
                block_type=block_type,
                bounds=region.bounds,
                reading_order=region.reading_order,
                text=clean_text,
                confidence=output.mean_confidence,
                words=output.words,
            )
            blocks.append(block)
            all_words.extend(output.words)

        ordered_blocks = tuple(sorted(blocks, key=lambda block: block.reading_order))
        raw_text = _reconstruct_text(ordered_blocks)
        words = tuple(all_words)
        confidence = statistics.fmean(word.confidence for word in words) if words else None
        warnings = set(_warnings(raw_text, words, ordered_blocks, layout))
        if removed_structured_rows:
            warnings.add("STRUCTURED_ROW_ARTIFACTS_REMOVED")
        return OcrAttempt(
            strategy="REGION_PSM_6",
            psm=6,
            preprocessing=preprocessing,
            raw_text=raw_text,
            mean_confidence=confidence,
            score=_score(raw_text, words, warnings, region_strategy=True),
            warnings=tuple(sorted(warnings)),
            blocks=ordered_blocks,
            words=words,
        )

    @property
    def tesseract(self) -> TesseractRunner:
        return self._tesseract


def _blocks_from_words(
        words: tuple[OcrWord, ...],
        layout: LayoutAnalysis,
) -> tuple[OcrBlock, ...]:
    blocks = [_illustration_block(region) for region in layout.illustration_regions]
    assignments: dict[int, list[OcrWord]] = {
        region.region_id: [] for region in layout.text_regions
    }
    for word in words:
        candidates = [
            region for region in layout.text_regions
            if _word_in_region(word, region.bounds)
        ]
        if candidates:
            selected = min(candidates, key=lambda region: region.bounds.area)
            assignments[selected.region_id].append(word)
    for region in layout.text_regions:
        region_words = tuple(assignments[region.region_id])
        if not region_words:
            continue
        confidence = statistics.fmean(word.confidence for word in region_words)
        text = _text_from_words(region_words)
        blocks.append(OcrBlock(
            block_id=region.region_id,
            block_type=_refine_type(region.region_type, text),
            bounds=region.bounds,
            reading_order=region.reading_order,
            text=text,
            confidence=confidence,
            words=region_words,
        ))
    return tuple(sorted(blocks, key=lambda block: block.reading_order))


def _illustration_block(region) -> OcrBlock:
    return OcrBlock(
        block_id=region.region_id,
        block_type=OcrBlockType.ILLUSTRATION,
        bounds=region.bounds,
        reading_order=region.reading_order,
        text="",
        confidence=None,
    )


def _text_from_words(words: tuple[OcrWord, ...]) -> str:
    lines: dict[tuple[int, int, int, int], list[OcrWord]] = {}
    for word in words:
        key = (word.page_number, word.block_number, word.paragraph_number, word.line_number)
        lines.setdefault(key, []).append(word)
    return "\n".join(
        " ".join(word.text for word in sorted(line, key=lambda item: (item.left, item.word_number)))
        for _, line in sorted(
            lines.items(),
            key=lambda item: (
                min(word.top for word in item[1]),
                min(word.left for word in item[1]),
            ),
        )
    )


def _reconstruct_text(blocks: tuple[OcrBlock, ...] | list[OcrBlock]) -> str:
    return "\n\n".join(
        block.text.strip()
        for block in sorted(blocks, key=lambda item: item.reading_order)
        if block.block_type is not OcrBlockType.ILLUSTRATION and block.text.strip()
    )


def _word_in_region(word: OcrWord, bounds) -> bool:
    center_x = word.left + word.width / 2
    center_y = word.top + word.height / 2
    return bounds.left <= center_x <= bounds.right and bounds.top <= center_y <= bounds.bottom


def _refine_type(block_type: OcrBlockType, text: str) -> OcrBlockType:
    normalized = "".join(character for character in text if character.isalnum())
    if block_type in {OcrBlockType.HEADER, OcrBlockType.PAGE_NUMBER}:
        return (
            OcrBlockType.PAGE_NUMBER
            if normalized.isdigit() and len(normalized) <= 4
            else OcrBlockType.HEADER
        )
    return block_type


def _warnings(
        text: str,
        words: tuple[OcrWord, ...],
        blocks: tuple[OcrBlock, ...] | list[OcrBlock],
        layout: LayoutAnalysis,
) -> tuple[str, ...]:
    warnings = set()
    if _TSV_ROW.search(text):
        warnings.add("STRUCTURED_ROW_ARTIFACTS")
    if any(_CYRILLIC.search(word.text) and _LATIN.search(word.text) for word in words):
        warnings.add("MIXED_SCRIPT_WORDS")
    if re.search(r"(\S)\1{4,}", text):
        warnings.add("GARBAGE_SEQUENCE")
    if words and sum(word.confidence < 0.60 for word in words) / len(words) >= 0.10:
        warnings.add("LOW_CONFIDENCE_WORDS")
    if any(block.confidence is not None and block.confidence < 0.65 for block in blocks):
        warnings.add("LOW_CONFIDENCE_REGION")
    if layout.illustration_regions and not blocks:
        warnings.add("MOSTLY_IMAGE")
    visible = [character for character in text if not character.isspace()]
    if visible:
        symbols = sum(unicodedata.category(character)[0] in {"P", "S"} for character in visible)
        if symbols / len(visible) >= 0.08:
            warnings.add("EXCESSIVE_SYMBOLS")
    return tuple(sorted(warnings))


def _remove_structured_rows(text: str) -> tuple[str, bool]:
    lines = text.splitlines()
    kept = [line for line in lines if not _TSV_ROW.fullmatch(line)]
    removed = len(kept) != len(lines)
    return "\n".join(kept).strip(), removed


def _score(
        text: str,
        words: tuple[OcrWord, ...],
        warnings: tuple[str, ...],
        *,
        region_strategy: bool,
) -> float:
    confidence = statistics.fmean(word.confidence for word in words) if words else 0.0
    length_score = min(len(words) / 80.0, 1.0)
    warning_penalty = min(len(warnings) * 0.08, 0.48)
    layout_bonus = 0.08 if region_strategy and words else 0.0
    empty_penalty = 0.50 if not text.strip() else 0.0
    return round(confidence * 0.65 + length_score * 0.27 + layout_bonus - warning_penalty - empty_penalty, 6)


def _selection_key(attempt: OcrAttempt) -> tuple[int, float, int]:
    """Prefer a clean attempt before comparing confidence-derived scores."""
    severe_warning_count = len(_SEVERE_SELECTION_WARNINGS.intersection(attempt.warnings))
    return (-severe_warning_count, attempt.score, -attempt.psm)


def _select_attempt(attempts: list[OcrAttempt]) -> OcrAttempt:
    least_severe = min(
        len(_SEVERE_SELECTION_WARNINGS.intersection(attempt.warnings))
        for attempt in attempts
    )
    eligible = [
        attempt for attempt in attempts
        if len(_SEVERE_SELECTION_WARNINGS.intersection(attempt.warnings))
        == least_severe
    ]
    best = max(eligible, key=_selection_key)
    full_page = [
        attempt for attempt in eligible
        if attempt.strategy.startswith("MASKED_PAGE_")
    ]
    if not full_page:
        return best

    best_full_page = max(full_page, key=_selection_key)
    psm_three = next((attempt for attempt in full_page if attempt.psm == 3), None)
    if psm_three is not None and best_full_page.score < psm_three.score + 0.03:
        # PSM 3 is Tesseract's automatic page-layout mode. Keep it for close
        # results because PSM 4 may interleave columns on mixed-layout pages.
        best_full_page = psm_three
    if best.strategy.startswith("MASKED_PAGE_"):
        return best_full_page
    # Region OCR is valuable for difficult layouts, but it may fragment lines.
    # Require a material deterministic advantage before replacing the native
    # masked-page reading order.
    if best.score < best_full_page.score + 0.12:
        return best_full_page
    return best
