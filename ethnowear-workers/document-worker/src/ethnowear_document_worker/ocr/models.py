from dataclasses import dataclass
from enum import StrEnum


class OcrBlockType(StrEnum):
    HEADER = "HEADER"
    BODY_TEXT = "BODY_TEXT"
    ILLUSTRATION = "ILLUSTRATION"
    CAPTION = "CAPTION"
    FOOTNOTE = "FOOTNOTE"
    PAGE_NUMBER = "PAGE_NUMBER"
    UNKNOWN_TEXT = "UNKNOWN_TEXT"


@dataclass(frozen=True, slots=True)
class BoundingBox:
    left: int
    top: int
    width: int
    height: int

    @property
    def right(self) -> int:
        return self.left + self.width

    @property
    def bottom(self) -> int:
        return self.top + self.height

    @property
    def area(self) -> int:
        return self.width * self.height


@dataclass(frozen=True, slots=True)
class OcrBlock:
    block_id: int
    block_type: OcrBlockType
    bounds: BoundingBox
    reading_order: int
    text: str
    confidence: float | None
    words: tuple["OcrWord", ...] = ()


@dataclass(frozen=True, slots=True)
class TsvParseDiagnostics:
    total_row_count: int
    rejected_row_count: int
    usable_word_count: int
    rejection_reasons: tuple[tuple[str, int], ...] = ()


@dataclass(frozen=True, slots=True)
class OcrAttempt:
    strategy: str
    psm: int
    preprocessing: tuple[str, ...]
    raw_text: str
    mean_confidence: float | None
    score: float
    warnings: tuple[str, ...]
    blocks: tuple[OcrBlock, ...]
    words: tuple["OcrWord", ...]
    tsv_diagnostics: TsvParseDiagnostics | None = None


@dataclass(frozen=True, slots=True)
class OcrWord:
    page_number: int
    block_number: int
    paragraph_number: int
    line_number: int
    word_number: int
    left: int
    top: int
    width: int
    height: int
    confidence: float
    text: str

    def with_offset(self, offset_x: int, offset_y: int) -> "OcrWord":
        return OcrWord(
            page_number=self.page_number,
            block_number=self.block_number,
            paragraph_number=self.paragraph_number,
            line_number=self.line_number,
            word_number=self.word_number,
            left=self.left + offset_x,
            top=self.top + offset_y,
            width=self.width,
            height=self.height,
            confidence=self.confidence,
            text=self.text,
        )


@dataclass(frozen=True, slots=True)
class FigureCandidate:
    candidate_ordinal: int
    bounds: BoundingBox
    normalized_x: float
    normalized_y: float
    normalized_width: float
    normalized_height: float
    detection_confidence: float | None
    candidate_type: str | None = None
    raw_caption_text: str | None = None


@dataclass(frozen=True, slots=True)
class OcrOutput:
    raw_text: str
    mean_confidence: float | None
    words: tuple[OcrWord, ...]
    engine_name: str
    engine_version: str
    language: str
    psm: int = 3
    strategy: str = "FULL_PAGE_PSM_3"
    preprocessing: tuple[str, ...] = ()
    warnings: tuple[str, ...] = ()
    blocks: tuple[OcrBlock, ...] = ()
    attempts: tuple[OcrAttempt, ...] = ()
    figure_candidates: tuple[FigureCandidate, ...] = ()
    tsv_diagnostics: TsvParseDiagnostics | None = None
