import csv
import io
import math
from collections import Counter

from ethnowear_document_worker.ocr.models import OcrWord, TsvParseDiagnostics

_REQUIRED_COLUMNS = (
    "level", "page_num", "block_num", "par_num", "line_num", "word_num",
    "left", "top", "width", "height", "conf", "text",
)
_INTEGER_COLUMNS = (
    "level", "page_num", "block_num", "par_num", "line_num", "word_num",
    "left", "top", "width", "height",
)
_MAX_REJECTED_ROWS = 10
_MAX_REJECTED_RATIO = 0.05
_MIN_ROWS_FOR_RATIO_LIMIT = 3


class InvalidTsvError(ValueError):
    def __init__(
            self,
            message: str,
            *,
            category: str = "invalid_tsv",
            rejected_row_count: int = 0,
            usable_word_count: int = 0,
            selected_psm: int | None = None,
    ) -> None:
        super().__init__(message)
        self.category = category
        self.rejected_row_count = rejected_row_count
        self.usable_word_count = usable_word_count
        self.selected_psm = selected_psm


def parse_tsv(tsv: str) -> tuple[str, float | None, tuple[OcrWord, ...]]:
    mean_confidence, words = parse_tsv_metadata(tsv)
    lines: dict[tuple[int, int, int, int], list[str]] = {}
    for word in words:
        key = (
            word.page_number,
            word.block_number,
            word.paragraph_number,
            word.line_number,
        )
        lines.setdefault(key, []).append(word.text)
    raw_text = "\n".join(" ".join(line) for line in lines.values())
    return raw_text, mean_confidence, words


def parse_tsv_metadata(tsv: str) -> tuple[float | None, tuple[OcrWord, ...]]:
    mean_confidence, words, _ = parse_tsv_metadata_with_diagnostics(tsv)
    return mean_confidence, words


def parse_tsv_metadata_with_diagnostics(
        tsv: str,
) -> tuple[float | None, tuple[OcrWord, ...], TsvParseDiagnostics]:
    # Tesseract TSV is tab-delimited but does not quote recognized text. Treating
    # a recognized double quote as CSV syntax can consume the rest of the file
    # as one multiline field, so quoting must be disabled explicitly.
    reader = csv.DictReader(
        io.StringIO(tsv),
        delimiter="\t",
        quoting=csv.QUOTE_NONE,
    )

    if reader.fieldnames is None:
        raise InvalidTsvError(
            "OCR output has no TSV header",
            category="missing_header",
        )

    if not set(_REQUIRED_COLUMNS).issubset(reader.fieldnames):
        raise InvalidTsvError(
            "OCR output has an invalid TSV header",
            category="invalid_header",
        )

    words: list[OcrWord] = []
    rejected = Counter[str]()
    total_rows = 0

    for row in reader:
        total_rows += 1
        reason, word = _parse_word_row(row)
        if reason is not None:
            rejected[reason] += 1
        elif word is not None:
            words.append(word)

    rejected_count = sum(rejected.values())
    diagnostics = TsvParseDiagnostics(
        total_row_count=total_rows,
        rejected_row_count=rejected_count,
        usable_word_count=len(words),
        rejection_reasons=tuple(sorted(rejected.items())),
    )

    if not words:
        raise InvalidTsvError(
            "OCR output contains no usable word rows",
            category="no_usable_words",
            rejected_row_count=rejected_count,
        )

    malformed_ratio = rejected_count / total_rows if total_rows else 0.0
    if (
            rejected_count > _MAX_REJECTED_ROWS
            or (
                rejected_count >= _MIN_ROWS_FOR_RATIO_LIMIT
                and malformed_ratio > _MAX_REJECTED_RATIO
            )
    ):
        raise InvalidTsvError(
            "OCR output contains excessive malformed rows",
            category="excessive_malformed_rows",
            rejected_row_count=rejected_count,
            usable_word_count=len(words),
        )

    mean_confidence = sum(word.confidence for word in words) / len(words)
    return mean_confidence, tuple(words), diagnostics


def _parse_word_row(
        row: dict[str | None, str | list[str] | None],
) -> tuple[str | None, OcrWord | None]:
    if None in row or any(row.get(column) is None for column in _REQUIRED_COLUMNS):
        return "column_count", None

    try:
        values = {column: int(str(row[column])) for column in _INTEGER_COLUMNS}
        confidence_percent = float(str(row["conf"]))
    except (TypeError, ValueError, KeyError):
        return "numeric_value", None

    if not math.isfinite(confidence_percent):
        return "confidence_value", None

    if values["level"] != 5:
        return None, None

    text = str(row["text"]).strip()
    if not text or confidence_percent < 0:
        return None, None

    # With QUOTE_NONE, embedded tabs always create extra columns and are rejected
    # above. Newlines always create a separate malformed record. Neither can be
    # smuggled into word text or plain transcription.
    if "\t" in text or "\n" in text:
        return "structured_text", None

    if any(values[column] < 0 for column in _INTEGER_COLUMNS):
        return "numeric_range", None

    confidence = min(confidence_percent, 100.0) / 100.0
    return None, OcrWord(
        page_number=values["page_num"],
        block_number=values["block_num"],
        paragraph_number=values["par_num"],
        line_number=values["line_num"],
        word_number=values["word_num"],
        left=values["left"],
        top=values["top"],
        width=values["width"],
        height=values["height"],
        confidence=confidence,
        text=text,
    )
