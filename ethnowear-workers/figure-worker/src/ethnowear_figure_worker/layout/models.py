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
class PixelBounds:
    left: int
    top: int
    width: int
    height: int

    def __post_init__(self) -> None:
        if self.left < 0 or self.top < 0:
            raise ValueError("Layout coordinates must not be negative")

        if self.width <= 0 or self.height <= 0:
            raise ValueError("Layout dimensions must be positive")

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
class LayoutWord:
    bounds: PixelBounds
    confidence: float
    text: str

    def __post_init__(self) -> None:
        if not 0.0 <= self.confidence <= 1.0:
            raise ValueError("Word confidence must be between zero and one")


@dataclass(frozen=True, slots=True)
class LayoutBlock:
    block_id: int
    block_type: OcrBlockType
    reading_order: int
    bounds: PixelBounds
    confidence: float | None
    text: str
    word_count: int

    def __post_init__(self) -> None:
        if self.block_id <= 0:
            raise ValueError("Layout block ID must be positive")

        if self.reading_order < 0:
            raise ValueError("Reading order must not be negative")

        if self.word_count < 0:
            raise ValueError("Word count must not be negative")

        if (
            self.confidence is not None
            and not 0.0 <= self.confidence <= 1.0
        ):
            raise ValueError("Block confidence must be between zero and one")


@dataclass(frozen=True, slots=True)
class ParsedOcrLayout:
    schema_version: int
    selected_strategy: str
    blocks: tuple[LayoutBlock, ...]
    words: tuple[LayoutWord, ...]

    @property
    def illustration_blocks(self) -> tuple[LayoutBlock, ...]:
        return tuple(
            block
            for block in self.blocks
            if block.block_type is OcrBlockType.ILLUSTRATION
        )

    @property
    def caption_blocks(self) -> tuple[LayoutBlock, ...]:
        return tuple(
            block
            for block in self.blocks
            if block.block_type is OcrBlockType.CAPTION
        )