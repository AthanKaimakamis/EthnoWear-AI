import json

from ethnowear_document_worker.ocr.models import (
    FigureCandidate,
    OcrAttempt,
    OcrBlock,
    OcrOutput,
    OcrWord,
)


class OcrOutputLimitError(ValueError):
    pass


def build_structured_output(output: OcrOutput, maximum_bytes: int) -> str:
    if maximum_bytes <= 0:
        raise ValueError("Maximum OCR output size must be positive")

    payload = {
        "schemaVersion": 2,
        "selectedStrategy": output.strategy,
        "warnings": list(output.warnings),
        "preprocessing": list(output.preprocessing),
        "words": [_word_payload(word) for word in output.words],
        "blocks": [_block_payload(block) for block in output.blocks],
        "attempts": [_attempt_payload(attempt) for attempt in output.attempts],
        "figureCandidates": [
            _figure_candidate_payload(candidate)
            for candidate in output.figure_candidates
        ],
    }

    serialized = json.dumps(
        payload,
        ensure_ascii=False,
        separators=(",", ":"),
    )

    if len(serialized.encode("utf-8")) > maximum_bytes:
        raise OcrOutputLimitError("OCR structured output exceeds the maximum size")

    return serialized

def build_parameters(
        *,
        language: str,
        oem: int,
        psm: int,
        preprocessed: bool,
        strategy: str | None = None,
        preprocessing: tuple[str, ...] = (),
) -> str:
    return json.dumps(
        {
            "language": language,
            "oem": oem,
            "psm": psm,
            "preprocessed": preprocessed,
            "strategy": strategy,
            "preprocessing": list(preprocessing),
        },
        ensure_ascii=False,
        separators=(",", ":"),
        sort_keys=True,
    )


def _word_payload(word: OcrWord) -> dict[str, object]:
    return {
        "page": word.page_number,
        "block": word.block_number,
        "paragraph": word.paragraph_number,
        "line": word.line_number,
        "word": word.word_number,
        "left": word.left,
        "top": word.top,
        "width": word.width,
        "height": word.height,
        "confidence": round(word.confidence, 4),
        "text": word.text,
    }


def _block_payload(block: OcrBlock) -> dict[str, object]:
    return {
        "id": block.block_id,
        "type": block.block_type.value,
        "readingOrder": block.reading_order,
        "left": block.bounds.left,
        "top": block.bounds.top,
        "width": block.bounds.width,
        "height": block.bounds.height,
        "confidence": round(block.confidence, 4) if block.confidence is not None else None,
        "text": block.text,
        "wordCount": len(block.words),
    }


def _figure_candidate_payload(
        candidate: FigureCandidate,
) -> dict[str, object]:
    return {
        "candidateOrdinal": candidate.candidate_ordinal,
        "candidateType": candidate.candidate_type,
        "left": candidate.bounds.left,
        "top": candidate.bounds.top,
        "width": candidate.bounds.width,
        "height": candidate.bounds.height,
        "normalizedX": round(candidate.normalized_x, 7),
        "normalizedY": round(candidate.normalized_y, 7),
        "normalizedWidth": round(candidate.normalized_width, 7),
        "normalizedHeight": round(candidate.normalized_height, 7),
        "detectionConfidence": (
            round(candidate.detection_confidence, 4)
            if candidate.detection_confidence is not None
            else None
        ),
        "rawCaptionText": candidate.raw_caption_text,
    }


def _attempt_payload(attempt: OcrAttempt) -> dict[str, object]:
    return {
        "strategy": attempt.strategy,
        "psm": attempt.psm,
        "preprocessing": list(attempt.preprocessing),
        "rawText": attempt.raw_text,
        "meanConfidence": (
            round(attempt.mean_confidence, 4)
            if attempt.mean_confidence is not None else None
        ),
        "score": attempt.score,
        "warnings": list(attempt.warnings),
        "blocks": [_block_payload(block) for block in attempt.blocks],
        "words": [_word_payload(word) for word in attempt.words],
    }
