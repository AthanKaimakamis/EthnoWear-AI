import json
from typing import Any

from ethnowear_figure_worker.layout.models import (
    LayoutBlock,
    LayoutWord,
    OcrBlockType,
    ParsedOcrLayout,
    PixelBounds,
)


class OcrLayoutParseError(ValueError):
    pass


def parse_ocr_layout(
        value: str | None,
        *,
        maximum_bytes: int,
        maximum_blocks: int = 512,
        maximum_words: int = 200_000,
) -> ParsedOcrLayout:
    if maximum_bytes <= 0:
        raise ValueError("Maximum OCR layout size must be positive")

    if maximum_blocks <= 0 or maximum_words <= 0:
        raise ValueError("OCR layout count limits must be positive")

    if value is None or not value.strip():
        raise OcrLayoutParseError("OCR layout is unavailable")

    if len(value.encode("utf-8")) > maximum_bytes:
        raise OcrLayoutParseError("OCR layout exceeds the maximum size")

    try:
        payload = json.loads(value)
    except json.JSONDecodeError as error:
        raise OcrLayoutParseError("OCR layout is invalid JSON") from error

    if not isinstance(payload, dict):
        raise OcrLayoutParseError("OCR layout root must be an object")

    if payload.get("schemaVersion") != 2:
        raise OcrLayoutParseError("OCR layout schema version is unsupported")

    blocks_payload = payload.get("blocks")
    words_payload = payload.get("words")

    if not isinstance(blocks_payload, list):
        raise OcrLayoutParseError("OCR layout blocks must be an array")

    if not isinstance(words_payload, list):
        raise OcrLayoutParseError("OCR layout words must be an array")

    if len(blocks_payload) > maximum_blocks:
        raise OcrLayoutParseError("OCR layout has too many blocks")

    if len(words_payload) > maximum_words:
        raise OcrLayoutParseError("OCR layout has too many words")

    try:
        blocks = tuple(
            _parse_block(item)
            for item in blocks_payload
        )
        words = tuple(
            _parse_word(item)
            for item in words_payload
        )
    except (
        KeyError,
        TypeError,
        ValueError,
    ) as error:
        raise OcrLayoutParseError("OCR layout contains invalid metadata") from error

    block_ids = {block.block_id for block in blocks}
    if len(block_ids) != len(blocks):
        raise OcrLayoutParseError("OCR layout block IDs must be unique")

    reading_orders = {
        block.reading_order
        for block in blocks
    }
    if len(reading_orders) != len(blocks):
        raise OcrLayoutParseError("OCR layout reading orders must be unique")

    strategy = payload.get("selectedStrategy")
    if not isinstance(strategy, str) or not strategy.strip():
        raise OcrLayoutParseError("OCR layout selected strategy is invalid")

    return ParsedOcrLayout(
        schema_version=2,
        selected_strategy=strategy,
        blocks=blocks,
        words=words,
    )


def _parse_block(value: Any) -> LayoutBlock:
    item = _object(value)

    return LayoutBlock(
        block_id=_integer(item, "id"),
        block_type=OcrBlockType(_string(item, "type")),
        reading_order=_integer(item, "readingOrder"),
        bounds=_bounds(item),
        confidence=_optional_confidence(item.get("confidence")),
        text=_string(item, "text", allow_empty=True),
        word_count=_integer(item, "wordCount"),
    )


def _parse_word(value: Any) -> LayoutWord:
    item = _object(value)

    return LayoutWord(
        bounds=_bounds(item),
        confidence=_confidence(item.get("confidence")),
        text=_string(item, "text"),
    )


def _bounds(item: dict[str, Any]) -> PixelBounds:
    return PixelBounds(
        left=_integer(item, "left"),
        top=_integer(item, "top"),
        width=_integer(item, "width"),
        height=_integer(item, "height"),
    )


def _object(value: Any) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise TypeError("Layout entry must be an object")

    return value


def _integer(item: dict[str, Any], name: str) -> int:
    value = item[name]

    if isinstance(value, bool) or not isinstance(value, int):
        raise TypeError(f"{name} must be an integer")

    return value


def _string(
        item: dict[str, Any],
        name: str,
        *,
        allow_empty: bool = False,
) -> str:
    value = item[name]

    if not isinstance(value, str):
        raise TypeError(f"{name} must be a string")

    if not allow_empty and not value.strip():
        raise ValueError(f"{name} must not be empty")

    return value


def _confidence(value: Any) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise TypeError("Confidence must be numeric")

    result = float(value)
    if not 0.0 <= result <= 1.0:
        raise ValueError("Confidence is outside the valid range")

    return result


def _optional_confidence(value: Any) -> float | None:
    if value is None:
        return None

    return _confidence(value)