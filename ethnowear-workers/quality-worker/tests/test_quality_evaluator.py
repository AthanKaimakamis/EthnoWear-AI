import json

import pytest

from ethnowear_quality_worker.api.models import (
    QualityAssessmentContextResponse,
    QualitySignalSeverity,
    QualityStatus,
)
from ethnowear_quality_worker.input.image import OcrImageInfo
from ethnowear_quality_worker.quality.evaluator import (
    QualityAssessmentError,
    evaluate_quality,
)
from ethnowear_quality_worker.quality.dictionary import BulgarianDictionary
from helpers import limits as make_limits


def structured_words(*, skew: float = 0.0, count: int = 30) -> str:
    words = []
    for index in range(count):
        line = index // 10 + 1
        left = (index % 10) * 100
        words.append({
            "page": 1,
            "block": 1,
            "paragraph": 1,
            "line": line,
            "word": index + 1,
            "left": left,
            "top": line * 100 + left * skew,
            "width": 80,
            "height": 40,
            "confidence": 0.92,
            "text": "шевица",
        })
    return json.dumps({"schemaVersion": 1, "words": words})


def context(
        *,
        text: str | None = None,
        confidence: float | None = 0.92,
        language: str = "bul",
        structured: str | None = None,
) -> QualityAssessmentContextResponse:
    return QualityAssessmentContextResponse(
        job_id=13,
        document_id=7,
        page_id=21,
        ocr_result_id=31,
        page_media_id=41,
        input_media_id=51,
        raw_ocr_text=(
            "Българската народна шевица съдържа традиционни геометрични "
            "орнаменти и регионални мотиви. " * 3
            if text is None
            else text
        ),
        ocr_confidence=confidence,
        ocr_language=language,
        structured_output_json=(
            structured_words()
            if structured is None
            else structured
        ),
        image_mime_type="image/png",
        image_size_bytes=50_000,
        image_width=2_000,
        image_height=3_000,
        image_dpi=300,
        image_color_mode="GRAYSCALE",
    )


def image() -> OcrImageInfo:
    return OcrImageInfo(
        format="PNG",
        width=2_000,
        height=3_000,
        mode="L",
    )


def dictionary() -> BulgarianDictionary:
    return BulgarianDictionary(frozenset({"шевица"}))


def signal(assessment, signal_type: str):
    return next(
        item for item in assessment.signals
        if item.signal_type == signal_type
    )


def test_evaluate_quality_passes_good_bulgarian_ocr() -> None:
    assessment = evaluate_quality(
        context(),
        image(),
        make_limits(),
        dictionary(),
    )

    assert assessment.quality_status is QualityStatus.PASS
    assert assessment.overall_score == 1.0
    assert len(assessment.signals) == 18
    assert signal(assessment, "CYRILLIC_RATIO").decimal_value > 0.9
    assert signal(assessment, "IMAGE_SKEW").severity is QualitySignalSeverity.INFO


def test_evaluate_quality_fails_empty_ocr() -> None:
    assessment = evaluate_quality(
        context(text="", confidence=None, structured=json.dumps({
            "schemaVersion": 1,
            "words": [],
        })),
        image(),
        make_limits(),
    )

    assert assessment.quality_status is QualityStatus.FAIL
    assert signal(assessment, "TEXT_LENGTH").severity is QualitySignalSeverity.ERROR
    assert signal(assessment, "WORD_COUNT").text_value == "0"


def test_evaluate_quality_requires_review_for_language_mismatch() -> None:
    assessment = evaluate_quality(
        context(text="English Latin text without Cyrillic characters. " * 5),
        image(),
        make_limits(),
    )

    assert assessment.quality_status is QualityStatus.REVIEW_REQUIRED
    assert signal(
        assessment,
        "OCR_LANGUAGE_MISMATCH",
    ).severity is QualitySignalSeverity.ERROR


def test_evaluate_quality_detects_repeated_garbage_and_skew() -> None:
    assessment = evaluate_quality(
        context(
            text="Български текст !!!!! повтарящ се текст " * 5,
            structured=structured_words(skew=0.1),
        ),
        image(),
        make_limits(),
    )

    assert signal(
        assessment,
        "REPEATED_GARBAGE",
    ).severity is QualitySignalSeverity.ERROR
    assert signal(
        assessment,
        "IMAGE_SKEW",
    ).severity is QualitySignalSeverity.ERROR


def test_evaluate_quality_rejects_invalid_structured_output() -> None:
    with pytest.raises(QualityAssessmentError, match="structured output"):
        evaluate_quality(
            context(structured="not-json"),
            image(),
            make_limits(),
        )


def test_evaluate_quality_enforces_payload_make_limits() -> None:
    limits = make_limits()
    limits.maximum_quality_signals = 1

    with pytest.raises(QualityAssessmentError, match="too many signals"):
        evaluate_quality(context(), image(), limits)


def test_evaluate_quality_stores_large_counts_as_text() -> None:
    assessment = evaluate_quality(
        context(
            text="дума " * 4_000,
            structured=structured_words(count=1_200),
        ),
        image(),
        make_limits(),
    )

    assert signal(assessment, "TEXT_LENGTH").text_value == "20000"
    assert signal(assessment, "TEXT_LENGTH").decimal_value is None
    assert signal(assessment, "WORD_COUNT").text_value == "1200"
    assert signal(assessment, "WORD_COUNT").decimal_value is None


def test_high_confidence_mixed_script_tail_requires_review() -> None:
    payload = json.loads(structured_words(count=30))
    payload["words"].extend([
        {
            "page": 1,
            "block": 1,
            "paragraph": 1,
            "line": 99,
            "word": index,
            "left": index * 100,
            "top": 900,
            "width": 80,
            "height": 40,
            "confidence": 0.96,
            "text": word,
        }
        for index, word in enumerate(
            ("Аопре3ха", "Tadeusz", "Dobrowolscy", "Кrakow"),
            start=1,
        )
    ])

    assessment = evaluate_quality(
        context(
            text=("Български текст за народната шевица. " * 20)
                 + "Аопре3ха Tadeusz Dobrowolscy Кrakow.",
            confidence=0.96,
            structured=json.dumps(payload),
        ),
        image(),
        make_limits(),
        dictionary(),
    )

    assert assessment.quality_status is QualityStatus.REVIEW_REQUIRED
    assert signal(assessment, "MIXED_SCRIPT_WORD_RATIO").severity is QualitySignalSeverity.ERROR
    assert signal(assessment, "LINE_SCRIPT_OUTLIER_RATIO").severity is QualitySignalSeverity.ERROR
    assert signal(assessment, "CONFIDENCE_VALIDITY_DISAGREEMENT").severity is QualitySignalSeverity.ERROR


def test_schema_two_layout_warning_requires_review() -> None:
    payload = json.loads(structured_words())
    payload.update({
        "schemaVersion": 2,
        "warnings": ["LOW_CONFIDENCE_REGION"],
        "blocks": [{"type": "ILLUSTRATION"}],
    })

    assessment = evaluate_quality(
        context(structured=json.dumps(payload)),
        image(),
        make_limits(),
        dictionary(),
    )

    assert assessment.quality_status is QualityStatus.REVIEW_REQUIRED
    assert signal(assessment, "OCR_LAYOUT_WARNINGS").severity is QualitySignalSeverity.ERROR
    assert signal(assessment, "ILLUSTRATION_REGION_COUNT").text_value == "1"
