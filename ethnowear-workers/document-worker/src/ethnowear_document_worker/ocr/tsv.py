import csv
import io
import re

from ethnowear_document_worker.ocr.models import OcrWord

_REQUIRED_COLUMNS = {
    "level",
    "page_num",
    "block_num",
    "par_num",
    "line_num",
    "word_num",
    "left",
    "top",
    "width",
    "height",
    "conf",
    "text",
}
_TSV_SHAPED_TEXT = re.compile(r"^\d(?:\t[^\t]*){10,}$")


class InvalidTsvError(ValueError):
    pass


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
    reader = csv.DictReader(io.StringIO(tsv), delimiter="\t")

    if reader.fieldnames is None:
        raise InvalidTsvError("OCR output has no TSV header")

    if not _REQUIRED_COLUMNS.issubset(reader.fieldnames):
        raise InvalidTsvError("OCR output has an invalid TSV header")

    words: list[OcrWord] = []

    try:
        for row in reader:
            if None in row or any(row[column] is None for column in _REQUIRED_COLUMNS):
                raise InvalidTsvError("OCR output contains an invalid TSV row")

            if int(row["level"]) != 5:
                continue

            text = row["text"].strip()

            if not text:
                continue

            # Plain transcription is reconstructed from these words. A malformed
            # or quoted TSV record must therefore never be accepted as word text.
            if "\t" in text or "\n" in text or _TSV_SHAPED_TEXT.fullmatch(text):
                raise InvalidTsvError("OCR output contains structured word text")

            confidence_percent = float(row["conf"])

            # Tesseract uses -1 for row without word confidence.
            if confidence_percent < 0:
                continue

            confidence = min(confidence_percent, 100.0) / 100.0

            word = OcrWord(
                page_number=int(row["page_num"]),
                block_number=int(row["block_num"]),
                paragraph_number=int(row["par_num"]),
                line_number=int(row["line_num"]),
                word_number=int(row["word_num"]),
                left=int(row["left"]),
                top=int(row["top"]),
                width=int(row["width"]),
                height=int(row["height"]),
                confidence=confidence,
                text=text,
            )

            words.append(word)

    except (TypeError, ValueError, KeyError) as error:
        raise InvalidTsvError("OCR output contains an invalid TSV row") from error

    mean_confidence = (
        sum(word.confidence for word in words) / len(words)
        if words
        else None
    )

    return mean_confidence, tuple(words)
