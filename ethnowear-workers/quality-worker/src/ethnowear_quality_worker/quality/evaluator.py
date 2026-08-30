import json
import math
import re
import statistics
import unicodedata

from ethnowear_quality_worker.api.models import (
    QualityAssessmentContextResponse,
    QualityAssessmentRequest,
    QualitySignalRequest,
    QualitySignalSeverity,
    QualityStatus,
)
from ethnowear_worker_common.api.models import ResourceLimits
from ethnowear_quality_worker.input.image import OcrImageInfo
from ethnowear_quality_worker.quality.dictionary import BulgarianDictionary

ASSESSOR_NAME = "ethnowear-deterministic-quality"
ASSESSOR_VERSION = "1.1.0"
SCORE_VERSION = "2"

_CYRILLIC_PATTERN = re.compile(r"[\u0400-\u052f]")
_LATIN_PATTERN = re.compile(r"[A-Za-z]")
_REPEATED_CHARACTER_PATTERN = re.compile(r"(\S)\1{4,}")
_TOKEN_PATTERN = re.compile(r"\S+")


class QualityAssessmentError(ValueError):
    pass


def evaluate_quality(
        context: QualityAssessmentContextResponse,
        image: OcrImageInfo,
        limits: ResourceLimits,
        dictionary: BulgarianDictionary | None = None,
) -> QualityAssessmentRequest:
    words, line_count, skew_degrees, layout_warnings, illustration_count = _parse_layout(
        context.structured_output_json,
        limits.maximum_ocr_context_bytes,
    )
    text = context.raw_ocr_text
    text_length = len(text)
    cyrillic_ratio = _cyrillic_ratio(text)
    suspicious_ratio = _suspicious_ratio(text)
    repeated_garbage = _has_repeated_garbage(text)
    language_mismatch = _language_mismatch(
        context.ocr_language,
        cyrillic_ratio,
        text_length,
    )
    linguistic_signals = _linguistic_signals(words, text, dictionary)

    signals = (
        _confidence_signal(context.ocr_confidence),
        _text_length_signal(text_length),
        _ratio_signal(
            "CYRILLIC_RATIO",
            cyrillic_ratio,
            0.60,
            0.30,
            "Cyrillic character ratio was measured",
        ),
        _ratio_signal(
            "SUSPICIOUS_CHARACTER_RATIO",
            1.0 - suspicious_ratio,
            0.995,
            0.98,
            "Suspicious character ratio was measured",
            stored_value=suspicious_ratio,
        ),
        QualitySignalRequest(
            type="REPEATED_GARBAGE",
            text_value="detected" if repeated_garbage else "not_detected",
            severity=(
                QualitySignalSeverity.ERROR
                if repeated_garbage
                else QualitySignalSeverity.INFO
            ),
            weight=0.10,
            safe_message="Repeated OCR garbage patterns were checked",
        ),
        _count_signal("WORD_COUNT", len(words), minimum=20, weight=0.10),
        _count_signal("LINE_COUNT", line_count, minimum=3, weight=0.05),
        _resolution_signal(context, image),
        _skew_signal(skew_degrees),
        _layout_warning_signal(layout_warnings),
        QualitySignalRequest(
            type="ILLUSTRATION_REGION_COUNT",
            text_value=str(illustration_count),
            severity=QualitySignalSeverity.INFO,
            weight=0.02,
            safe_message="Detected illustration regions were counted",
        ),
        QualitySignalRequest(
            type="OCR_LANGUAGE_MISMATCH",
            text_value="detected" if language_mismatch else "not_detected",
            severity=(
                QualitySignalSeverity.ERROR
                if language_mismatch
                else QualitySignalSeverity.INFO
            ),
            weight=0.10,
            safe_message="OCR language and detected script were compared",
        ),
        *linguistic_signals,
    )

    status = _classify(signals, text_length, context.ocr_confidence)
    assessment = QualityAssessmentRequest(
        assessor_name=ASSESSOR_NAME,
        assessor_version=ASSESSOR_VERSION,
        score_version=SCORE_VERSION,
        overall_score=_overall_score(signals),
        quality_status=status,
        summary=_summary(status),
        limitations=(
            "Deterministic checks estimate OCR readability and do not approve transcription"
        ),
        signals=signals,
    )
    _validate_limits(assessment, limits)
    return assessment


def _parse_layout(
        structured_output_json: str | None,
        maximum_bytes: int,
) -> tuple[list[dict[str, object]], int, float | None, tuple[str, ...], int]:
    if structured_output_json is None:
        return [], 0, None, (), 0

    if len(structured_output_json.encode("utf-8")) > maximum_bytes:
        raise QualityAssessmentError("OCR context exceeds the configured limit")

    try:
        payload = json.loads(structured_output_json)
    except (TypeError, ValueError) as error:
        raise QualityAssessmentError("OCR structured output is invalid") from error

    if not isinstance(payload, dict) or payload.get("schemaVersion") not in {1, 2}:
        raise QualityAssessmentError("OCR structured output is invalid")

    words = payload.get("words")
    if not isinstance(words, list):
        raise QualityAssessmentError("OCR structured output is invalid")

    lines: set[tuple[object, object, object, object]] = set()
    line_points: dict[tuple[object, object, object, object], list[tuple[float, float]]] = {}

    for word in words:
        if not isinstance(word, dict):
            raise QualityAssessmentError("OCR structured output is invalid")

        key = (
            word.get("page"),
            word.get("block"),
            word.get("paragraph"),
            word.get("line"),
        )
        lines.add(key)

        try:
            center_x = float(word["left"]) + float(word["width"]) / 2
            center_y = float(word["top"]) + float(word["height"]) / 2
        except (KeyError, TypeError, ValueError) as error:
            raise QualityAssessmentError("OCR structured output is invalid") from error

        line_points.setdefault(key, []).append((center_x, center_y))

    angles = [
        angle
        for points in line_points.values()
        if (angle := _line_angle(points)) is not None
    ]
    skew = statistics.median(angles) if angles else None
    warnings = payload.get("warnings", [])
    blocks = payload.get("blocks", [])
    if not isinstance(warnings, list) or not all(isinstance(item, str) for item in warnings):
        raise QualityAssessmentError("OCR structured output is invalid")
    if not isinstance(blocks, list):
        raise QualityAssessmentError("OCR structured output is invalid")
    illustration_count = sum(
        isinstance(block, dict) and block.get("type") == "ILLUSTRATION"
        for block in blocks
    )
    return words, len(lines), skew, tuple(warnings), illustration_count


def _layout_warning_signal(warnings: tuple[str, ...]) -> QualitySignalRequest:
    corruption = {
        "EXCESSIVE_SYMBOLS",
        "GARBAGE_SEQUENCE",
        "LOW_CONFIDENCE_REGION",
        "MIXED_SCRIPT_WORDS",
        "MOSTLY_IMAGE",
    }
    severity = QualitySignalSeverity.INFO
    if corruption.intersection(warnings):
        severity = QualitySignalSeverity.ERROR
    elif warnings:
        severity = QualitySignalSeverity.WARNING
    return QualitySignalRequest(
        type="OCR_LAYOUT_WARNINGS",
        text_value=",".join(sorted(warnings)) if warnings else "none",
        severity=severity,
        weight=0.15,
        safe_message="OCR layout and region warnings were evaluated",
    )


def _line_angle(points: list[tuple[float, float]]) -> float | None:
    if len(points) < 3:
        return None

    mean_x = statistics.fmean(point[0] for point in points)
    mean_y = statistics.fmean(point[1] for point in points)
    denominator = sum((point[0] - mean_x) ** 2 for point in points)
    if denominator == 0:
        return None

    slope = sum(
        (point[0] - mean_x) * (point[1] - mean_y)
        for point in points
    ) / denominator
    return abs(math.degrees(math.atan(slope)))


def _cyrillic_ratio(text: str) -> float:
    letters = [character for character in text if character.isalpha()]
    if not letters:
        return 0.0

    return sum(bool(_CYRILLIC_PATTERN.fullmatch(character)) for character in letters) / len(letters)


def _suspicious_ratio(text: str) -> float:
    if not text:
        return 0.0

    suspicious = sum(
        character == "�"
        or (unicodedata.category(character).startswith("C") and not character.isspace())
        for character in text
    )
    return suspicious / len(text)


def _has_repeated_garbage(text: str) -> bool:
    if _REPEATED_CHARACTER_PATTERN.search(text):
        return True

    tokens = [token.casefold() for token in _TOKEN_PATTERN.findall(text)]
    return any(
        len(token) <= 3 and tokens[index:index + 5] == [token] * 5
        for index, token in enumerate(tokens[:-4])
    )


def _linguistic_signals(
        words: list[dict[str, object]],
        text: str,
        dictionary: BulgarianDictionary | None,
) -> tuple[QualitySignalRequest, ...]:
    word_texts = [str(word.get("text", "")).strip() for word in words]
    mixed_words = [word for word in word_texts if _is_mixed_script(word)]
    mixed_ratio = len(mixed_words) / len(word_texts) if word_texts else 0.0
    symbol_ratio = _symbol_ratio(text)
    dictionary_ratio, high_confidence_invalid_ratio = _dictionary_ratios(
        words,
        dictionary,
    )
    line_script_outlier_ratio, line_confidence_outlier_ratio = _line_outlier_ratios(words)

    return (
        _dictionary_signal(dictionary_ratio, dictionary is not None),
        _anomaly_signal(
            "MIXED_SCRIPT_WORD_RATIO",
            mixed_ratio,
            warning_threshold=0.001,
            error_threshold=0.01,
            weight=0.15,
            message="Words containing mixed Cyrillic and Latin scripts were measured",
        ),
        _anomaly_signal(
            "EXCESSIVE_SYMBOL_RATIO",
            symbol_ratio,
            warning_threshold=0.03,
            error_threshold=0.08,
            weight=0.10,
            message="Punctuation and symbol density was measured",
        ),
        _anomaly_signal(
            "CONFIDENCE_VALIDITY_DISAGREEMENT",
            high_confidence_invalid_ratio,
            warning_threshold=0.03,
            error_threshold=0.05,
            weight=0.15,
            message="High-confidence words were compared with linguistic validity",
        ),
        _anomaly_signal(
            "LINE_SCRIPT_OUTLIER_RATIO",
            line_script_outlier_ratio,
            warning_threshold=0.01,
            error_threshold=0.05,
            weight=0.15,
            message="Cyrillic-to-Latin ratios were compared between lines",
        ),
        _anomaly_signal(
            "LINE_CONFIDENCE_OUTLIER_RATIO",
            line_confidence_outlier_ratio,
            warning_threshold=0.05,
            error_threshold=0.15,
            weight=0.10,
            message="Word-confidence distributions were compared between lines",
        ),
    )


def _dictionary_ratios(
        words: list[dict[str, object]],
        dictionary: BulgarianDictionary | None,
) -> tuple[float | None, float]:
    candidates: list[tuple[str, float]] = []
    for word in words:
        normalized = _letters_only(str(word.get("text", "")))
        if len(normalized) < 3 or not _CYRILLIC_PATTERN.search(normalized):
            continue
        try:
            confidence = float(word.get("confidence", 0.0))
        except (TypeError, ValueError):
            confidence = 0.0
        candidates.append((normalized, confidence))

    if dictionary is None or not candidates:
        return None, 0.0

    valid = [dictionary.contains(word) for word, _ in candidates]
    high_confidence = [
        is_valid
        for is_valid, (_, confidence) in zip(valid, candidates, strict=True)
        if confidence >= 0.85
    ]
    disagreement = (
        sum(not is_valid for is_valid in high_confidence) / len(high_confidence)
        if high_confidence else 0.0
    )
    return sum(valid) / len(valid), disagreement


def _dictionary_signal(
        validity_ratio: float | None,
        available: bool,
) -> QualitySignalRequest:
    if not available or validity_ratio is None:
        return QualitySignalRequest(
            type="BULGARIAN_DICTIONARY_VALIDITY",
            text_value="unavailable",
            severity=QualitySignalSeverity.WARNING,
            weight=0.15,
            safe_message="Bulgarian dictionary validation was unavailable",
        )

    severity = QualitySignalSeverity.INFO
    if validity_ratio < 0.55:
        severity = QualitySignalSeverity.ERROR
    elif validity_ratio < 0.75:
        severity = QualitySignalSeverity.WARNING
    return QualitySignalRequest(
        type="BULGARIAN_DICTIONARY_VALIDITY",
        decimal_value=round(validity_ratio, 6),
        severity=severity,
        weight=0.15,
        safe_message="Bulgarian dictionary word validity was measured",
    )


def _anomaly_signal(
        signal_type: str,
        ratio: float,
        *,
        warning_threshold: float,
        error_threshold: float,
        weight: float,
        message: str,
) -> QualitySignalRequest:
    severity = QualitySignalSeverity.INFO
    if ratio >= error_threshold:
        severity = QualitySignalSeverity.ERROR
    elif ratio >= warning_threshold:
        severity = QualitySignalSeverity.WARNING
    return QualitySignalRequest(
        type=signal_type,
        decimal_value=round(ratio, 6),
        severity=severity,
        weight=weight,
        safe_message=message,
    )


def _line_outlier_ratios(
        words: list[dict[str, object]],
) -> tuple[float, float]:
    lines: dict[tuple[object, object, object, object], list[dict[str, object]]] = {}
    for word in words:
        key = (
            word.get("page"),
            word.get("block"),
            word.get("paragraph"),
            word.get("line"),
        )
        lines.setdefault(key, []).append(word)
    if len(lines) < 2:
        return 0.0, 0.0

    script_ratios = [_line_cyrillic_ratio(line) for line in lines.values()]
    confidence_means = [_line_confidence(line) for line in lines.values()]
    median_script = statistics.median(script_ratios)
    median_confidence = statistics.median(confidence_means)
    script_outliers = sum(
        abs(ratio - median_script) >= 0.50
        for ratio in script_ratios
    )
    confidence_outliers = sum(
        mean <= median_confidence - 0.20
        for mean in confidence_means
    )
    return script_outliers / len(lines), confidence_outliers / len(lines)


def _line_cyrillic_ratio(words: list[dict[str, object]]) -> float:
    text = "".join(str(word.get("text", "")) for word in words)
    cyrillic = len(_CYRILLIC_PATTERN.findall(text))
    latin = len(_LATIN_PATTERN.findall(text))
    return cyrillic / (cyrillic + latin) if cyrillic + latin else 0.0


def _line_confidence(words: list[dict[str, object]]) -> float:
    values: list[float] = []
    for word in words:
        try:
            values.append(float(word.get("confidence", 0.0)))
        except (TypeError, ValueError):
            continue
    return statistics.fmean(values) if values else 0.0


def _is_mixed_script(word: str) -> bool:
    return bool(_CYRILLIC_PATTERN.search(word) and _LATIN_PATTERN.search(word))


def _letters_only(word: str) -> str:
    return "".join(character for character in word.casefold() if character.isalpha())


def _symbol_ratio(text: str) -> float:
    visible = [character for character in text if not character.isspace()]
    if not visible:
        return 0.0
    symbols = sum(
        unicodedata.category(character)[0] in {"P", "S"}
        for character in visible
    )
    return symbols / len(visible)


def _language_mismatch(
        language: str | None,
        cyrillic_ratio: float,
        text_length: int,
) -> bool:
    return (
            language is not None
            and "bul" in language.split("+")
            and text_length >= 100
            and cyrillic_ratio < 0.30
    )


def _confidence_signal(confidence: float | None) -> QualitySignalRequest:
    if confidence is None:
        return QualitySignalRequest(
            type="OCR_CONFIDENCE",
            text_value="unavailable",
            severity=QualitySignalSeverity.WARNING,
            weight=0.20,
            safe_message="OCR confidence was unavailable",
        )

    severity = QualitySignalSeverity.INFO
    if confidence < 0.65:
        severity = QualitySignalSeverity.ERROR
    elif confidence < 0.85:
        severity = QualitySignalSeverity.WARNING

    return QualitySignalRequest(
        type="OCR_CONFIDENCE",
        decimal_value=round(confidence, 6),
        severity=severity,
        weight=0.20,
        safe_message="Mean OCR word confidence was evaluated",
    )


def _text_length_signal(text_length: int) -> QualitySignalRequest:
    severity = QualitySignalSeverity.INFO
    if text_length == 0:
        severity = QualitySignalSeverity.ERROR
    elif text_length < 100:
        severity = QualitySignalSeverity.WARNING

    return QualitySignalRequest(
        type="TEXT_LENGTH",
        text_value=str(text_length),
        severity=severity,
        weight=0.10,
        safe_message="OCR text length was evaluated",
    )


def _ratio_signal(
        signal_type: str,
        quality_value: float,
        pass_threshold: float,
        warning_threshold: float,
        message: str,
        *,
        stored_value: float | None = None,
) -> QualitySignalRequest:
    severity = QualitySignalSeverity.INFO
    if quality_value < warning_threshold:
        severity = QualitySignalSeverity.ERROR
    elif quality_value < pass_threshold:
        severity = QualitySignalSeverity.WARNING

    return QualitySignalRequest(
        type=signal_type,
        decimal_value=round(
            quality_value if stored_value is None else stored_value,
            6,
        ),
        severity=severity,
        weight=0.10,
        safe_message=message,
    )


def _count_signal(
        signal_type: str,
        count: int,
        *,
        minimum: int,
        weight: float,
) -> QualitySignalRequest:
    severity = QualitySignalSeverity.INFO
    if count == 0:
        severity = QualitySignalSeverity.ERROR
    elif count < minimum:
        severity = QualitySignalSeverity.WARNING

    return QualitySignalRequest(
        type=signal_type,
        text_value=str(count),
        severity=severity,
        weight=weight,
        safe_message=f"{signal_type.replace('_', ' ').title()} was evaluated",
    )


def _resolution_signal(
        context: QualityAssessmentContextResponse,
        image: OcrImageInfo,
) -> QualitySignalRequest:
    dpi = context.image_dpi
    severity = QualitySignalSeverity.INFO
    if dpi is not None and dpi < 150:
        severity = QualitySignalSeverity.WARNING
    if min(image.width, image.height) < 500:
        severity = QualitySignalSeverity.WARNING

    return QualitySignalRequest(
        type="IMAGE_RESOLUTION",
        decimal_value=float(dpi) if dpi is not None else None,
        text_value=f"{image.width}x{image.height}",
        severity=severity,
        weight=0.05,
        safe_message="Image resolution was evaluated",
    )


def _skew_signal(skew_degrees: float | None) -> QualitySignalRequest:
    if skew_degrees is None:
        return QualitySignalRequest(
            type="IMAGE_SKEW",
            text_value="unavailable",
            severity=QualitySignalSeverity.INFO,
            weight=0.05,
            safe_message="Image skew could not be estimated from text lines",
        )

    severity = QualitySignalSeverity.INFO
    if skew_degrees > 5:
        severity = QualitySignalSeverity.ERROR
    elif skew_degrees > 2:
        severity = QualitySignalSeverity.WARNING

    return QualitySignalRequest(
        type="IMAGE_SKEW",
        decimal_value=round(skew_degrees, 6),
        severity=severity,
        weight=0.05,
        safe_message="Image skew was estimated from OCR text lines",
    )


def _classify(
        signals: tuple[QualitySignalRequest, ...],
        text_length: int,
        confidence: float | None,
) -> QualityStatus:
    if text_length == 0 or (confidence is not None and confidence < 0.40):
        return QualityStatus.FAIL

    severities = {signal.severity for signal in signals}
    if QualitySignalSeverity.ERROR in severities:
        return QualityStatus.REVIEW_REQUIRED
    if QualitySignalSeverity.WARNING in severities:
        return QualityStatus.WARNING
    return QualityStatus.PASS


def _overall_score(signals: tuple[QualitySignalRequest, ...]) -> float:
    values = {
        QualitySignalSeverity.INFO: 1.0,
        QualitySignalSeverity.WARNING: 0.6,
        QualitySignalSeverity.ERROR: 0.1,
    }
    weighted = sum(
        values[signal.severity] * (signal.weight or 0.0)
        for signal in signals
    )
    weights = sum(signal.weight or 0.0 for signal in signals)
    return round(weighted / weights if weights else 0.0, 4)


def _summary(status: QualityStatus) -> str:
    return {
        QualityStatus.PASS: "Deterministic OCR checks passed",
        QualityStatus.WARNING: "Deterministic OCR checks found minor warnings",
        QualityStatus.FAIL: "Deterministic OCR checks found unusable text",
        QualityStatus.REVIEW_REQUIRED: "Deterministic OCR checks require focused review",
    }[status]


def _validate_limits(
        assessment: QualityAssessmentRequest,
        limits: ResourceLimits,
) -> None:
    if len(assessment.signals) > limits.maximum_quality_signals:
        raise QualityAssessmentError("Quality assessment has too many signals")

    if len(assessment.summary or "") > limits.maximum_quality_summary_characters:
        raise QualityAssessmentError("Quality assessment summary exceeds the limit")

    if len(assessment.limitations or "") > limits.maximum_quality_limitations_characters:
        raise QualityAssessmentError("Quality assessment limitations exceed the limit")

    for signal in assessment.signals:
        if len(signal.signal_type) > limits.maximum_quality_signal_type_characters:
            raise QualityAssessmentError("Quality signal type exceeds the limit")
        if len(signal.text_value or "") > limits.maximum_quality_signal_text_characters:
            raise QualityAssessmentError("Quality signal text exceeds the limit")
        if len(signal.safe_message or "") > limits.maximum_quality_message_characters:
            raise QualityAssessmentError("Quality signal message exceeds the limit")

    payload = assessment.model_dump_json(by_alias=True).encode("utf-8")
    if len(payload) > limits.maximum_quality_assessment_payload_bytes:
        raise QualityAssessmentError("Quality assessment payload exceeds the limit")
