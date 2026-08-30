import json

import pytest

from ethnowear_figure_worker.layout.models import OcrBlockType
from ethnowear_figure_worker.layout.parser import (
    OcrLayoutParseError,
    parse_ocr_layout,
)


def payload() -> dict[str, object]:
    return {
        "schemaVersion": 2,
        "selectedStrategy": "REGION_PSM_6",
        "blocks": [
            {
                "id": 1,
                "type": "ILLUSTRATION",
                "readingOrder": 0,
                "left": 100,
                "top": 200,
                "width": 400,
                "height": 300,
                "confidence": None,
                "text": "",
                "wordCount": 0,
            },
            {
                "id": 2,
                "type": "CAPTION",
                "readingOrder": 1,
                "left": 120,
                "top": 520,
                "width": 360,
                "height": 40,
                "confidence": 0.91,
                "text": "Обр. 12 — Престилка",
                "wordCount": 4,
            },
        ],
        "words": [
            {
                "page": 1,
                "block": 2,
                "paragraph": 1,
                "line": 1,
                "word": 1,
                "left": 120,
                "top": 520,
                "width": 60,
                "height": 30,
                "confidence": 0.92,
                "text": "Обр.",
            }
        ],
    }


def serialize(value: dict[str, object] | list[object]) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def test_parses_selected_layout_without_attempt_payloads() -> None:
    layout = parse_ocr_layout(
        serialize(payload()),
        maximum_bytes=10_000,
    )

    assert layout.schema_version == 2
    assert layout.selected_strategy == "REGION_PSM_6"
    assert len(layout.blocks) == 2
    assert layout.illustration_blocks[0].block_type is OcrBlockType.ILLUSTRATION
    assert layout.caption_blocks[0].text == "Обр. 12 — Престилка"
    assert layout.words[0].text == "Обр."
    assert layout.words[0].bounds.right == 180


@pytest.mark.parametrize("value", [None, "", "  "])
def test_rejects_missing_layout(value: str | None) -> None:
    with pytest.raises(OcrLayoutParseError, match="unavailable"):
        parse_ocr_layout(value, maximum_bytes=100)


def test_rejects_invalid_json_without_exposing_content() -> None:
    with pytest.raises(OcrLayoutParseError, match="invalid JSON"):
        parse_ocr_layout("{OCR text", maximum_bytes=100)


def test_rejects_unsupported_schema() -> None:
    value = payload()
    value["schemaVersion"] = 3

    with pytest.raises(OcrLayoutParseError, match="unsupported"):
        parse_ocr_layout(serialize(value), maximum_bytes=10_000)


def test_enforces_exact_utf8_size_limit() -> None:
    serialized = serialize(payload())
    size = len(serialized.encode("utf-8"))

    assert parse_ocr_layout(
        serialized,
        maximum_bytes=size,
    ).schema_version == 2

    with pytest.raises(OcrLayoutParseError, match="maximum size"):
        parse_ocr_layout(serialized, maximum_bytes=size - 1)


def test_rejects_excessive_block_and_word_counts() -> None:
    serialized = serialize(payload())

    with pytest.raises(OcrLayoutParseError, match="too many blocks"):
        parse_ocr_layout(
            serialized,
            maximum_bytes=10_000,
            maximum_blocks=1,
        )

    excessive_words = payload()
    excessive_words["words"].append(  # type: ignore[union-attr]
        {
            "page": 1,
            "block": 2,
            "paragraph": 1,
            "line": 1,
            "word": 2,
            "left": 185,
            "top": 520,
            "width": 40,
            "height": 30,
            "confidence": 0.90,
            "text": "12",
        }
    )

    with pytest.raises(OcrLayoutParseError, match="too many words"):
        parse_ocr_layout(
            serialize(excessive_words),
            maximum_bytes=10_000,
            maximum_words=1,
        )


def test_rejects_duplicate_block_identity_and_reading_order() -> None:
    duplicate_id = payload()
    duplicate_id["blocks"][1]["id"] = 1  # type: ignore[index]

    with pytest.raises(OcrLayoutParseError, match="IDs must be unique"):
        parse_ocr_layout(serialize(duplicate_id), maximum_bytes=10_000)

    duplicate_order = payload()
    duplicate_order["blocks"][1]["readingOrder"] = 0  # type: ignore[index]

    with pytest.raises(OcrLayoutParseError, match="orders must be unique"):
        parse_ocr_layout(serialize(duplicate_order), maximum_bytes=10_000)


@pytest.mark.parametrize(
    ("section", "field", "value"),
    [
        ("blocks", "width", 0),
        ("blocks", "type", "UNKNOWN_FUTURE_TYPE"),
        ("blocks", "confidence", 1.1),
        ("words", "confidence", -0.1),
        ("words", "text", ""),
    ],
)
def test_rejects_invalid_layout_metadata(
        section: str,
        field: str,
        value: object,
) -> None:
    document = payload()
    document[section][0][field] = value  # type: ignore[index]

    with pytest.raises(OcrLayoutParseError, match="invalid metadata"):
        parse_ocr_layout(serialize(document), maximum_bytes=10_000)
