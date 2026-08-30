import json

import pytest

from ethnowear_worker_common.api.errors import WorkerApiContractError
from ethnowear_worker_common.api.models import ResourceLimits
from ethnowear_vision_worker.api.models import QualityStatus
from ethnowear_vision_worker.assessment.mapper import build_assessment_request
from ethnowear_vision_worker.assessment.validation import (
    VisionResultOutcome,
    prepare_model_result,
)
from ethnowear_vision_worker.config import VisionWorkerSettings
from ethnowear_vision_worker.ollama.models import VisionModelResult
from ethnowear_vision_worker.ollama.models import OllamaDiagnostics


MODEL_VERSION = "a" * 64


def settings() -> VisionWorkerSettings:
    return VisionWorkerSettings(
        api_base_url="http://api:8080",
        worker_id="vision-worker-1",
        api_token="x" * 32,
        ollama_base_url="http://ollama:11434",
        vision_model="gemma3:4b",
        prompt_version="vision-ocr-v2",
    )


def limits() -> ResourceLimits:
    return ResourceLimits(
        maximum_input_bytes=10_000_000,
        maximum_page_count=500,
        render_dpi=300,
        maximum_pixel_width=20_000,
        maximum_pixel_height=20_000,
        maximum_page_pixels=100_000_000,
        maximum_rendition_bytes=10_000_000,
        maximum_ocr_text_characters=2_000_000,
        maximum_ocr_output_bytes=10_000_000,
        maximum_ocr_context_bytes=16_000_000,
        maximum_quality_assessment_payload_bytes=1_000_000,
        maximum_quality_signals=100,
        maximum_quality_signal_type_characters=100,
        maximum_quality_signal_text_characters=500,
        maximum_quality_summary_characters=1_000,
        maximum_quality_limitations_characters=1_000,
        maximum_quality_message_characters=1_000,
        maximum_vision_assessment_payload_bytes=10_000_000,
        maximum_vision_suggestion_characters=2_000_000,
        maximum_vision_issues_json_characters=4_000,
        maximum_vision_issues=100,
        maximum_vision_uncertain_passages=100,
        maximum_vision_issue_code_characters=100,
        maximum_vision_excerpt_characters=500,
        maximum_vision_reason_characters=1_000,
        maximum_vision_model_name_characters=100,
        maximum_vision_model_version_characters=100,
        maximum_vision_prompt_version_characters=100,
        maximum_indexing_content_characters=10_000,
        maximum_embedding_dimensions=4_096,
        maximum_embedding_model_characters=100,
        maximum_vector_collection_characters=150,
        maximum_vector_point_id_characters=255,
        job_timeout_seconds=600,
        heartbeat_interval_seconds=30,
        maximum_lease_seconds=120,
    )


def result(
    *,
    requires_review: bool = True,
    issues: list[dict[str, object]] | None = None,
    uncertain_passages: list[dict[str, object]] | None = None,
) -> VisionModelResult:
    return VisionModelResult.model_validate(
        {
            "score": 0.723456,
            "requiresReview": requires_review,
            "issues": issues or [],
            "uncertainPassages": uncertain_passages or [],
        }
    )


def issue() -> dict[str, object]:
    return {
        "type": "OCR_GARBAGE",
        "excerpt": "719 1604",
        "suggestedReplacement": "корекция",
        "reason": "Likely illustration-generated text",
        "confidence": 0.954321,
    }


def uncertain_passage() -> dict[str, object]:
    return {
        "excerpt": "неясен откъс",
        "reason": "The scan is blurred",
        "confidence": 0.654321,
    }


def test_maps_model_result_to_spring_assessment_contract() -> None:
    request = build_assessment_request(
        result(issues=[issue()], uncertain_passages=[uncertain_passage()]),
        MODEL_VERSION,
        settings(),
        limits(),
        suggested_text="Непроменен OCR текст.",
    )

    assert request.requires_review is True
    assert request.model_name == "gemma3:4b"
    assert request.model_version == MODEL_VERSION
    assert request.prompt_version == "vision-ocr-v2"
    assert request.suggested_text == "Непроменен OCR текст."
    assert request.assessment.assessor_name == request.model_name
    assert request.assessment.assessor_version == request.model_version
    assert request.assessment.overall_score == 0.7235
    assert request.assessment.quality_status is QualityStatus.REVIEW_REQUIRED
    assert request.issues[0].code == "OCR_GARBAGE"
    assert request.issues[0].suggested_text == "корекция"
    assert request.issues[0].confidence == 0.9543
    assert request.uncertain_passages[0].confidence == 0.6543


def test_maps_actionable_issue_fields() -> None:
    actionable = issue()
    actionable.update({
        "excerpt": "Контекст преди 719 1604 след него",
        "originalSubstring": "719 1604",
        "startOffset": 15,
        "endOffset": 23,
        "reason": "Числата са породени от изображението",
    })
    request = build_assessment_request(
        result(issues=[actionable]),
        MODEL_VERSION,
        settings(),
        limits(),
        suggested_text="Непроменен OCR текст.",
    )

    mapped = request.issues[0]
    assert mapped.original_context == "Контекст преди 719 1604 след него"
    assert mapped.original_text == "719 1604"
    assert mapped.suggested_context == "Контекст преди корекция след него"
    assert mapped.start_offset == 15
    assert mapped.end_offset == 23
    assert mapped.safely_applicable is True


def test_serializes_current_spring_issue_contract() -> None:
    request = build_assessment_request(
        result(issues=[issue()]), MODEL_VERSION, settings(), limits(),
        suggested_text="Непроменен OCR текст.",
    )

    body = request.model_dump(by_alias=True, mode="json", exclude_none=True)
    assert set(body["issues"][0]) == {
        "issueType", "explanationBg", "confidence", "originalText",
        "originalContext", "suggestedText", "suggestedContext",
        "safelyApplicable",
    }


def test_clean_result_remains_advisory_without_forcing_review() -> None:
    request = build_assessment_request(
        result(requires_review=False),
        MODEL_VERSION,
        settings(),
        limits(),
        suggested_text="Непроменен OCR текст.",
    )

    assert request.requires_review is False
    assert request.assessment.quality_status is QualityStatus.PASS
    assert "human transcription approval" in request.assessment.limitations


def test_issues_force_review_even_if_model_says_false() -> None:
    request = build_assessment_request(
        result(requires_review=False, issues=[issue()]),
        MODEL_VERSION,
        settings(),
        limits(),
        suggested_text="Непроменен OCR текст.",
    )

    assert request.requires_review is True
    assert request.assessment.quality_status is QualityStatus.REVIEW_REQUIRED


def test_maps_rejected_suggestion_as_sanitized_review_signal() -> None:
    request = build_assessment_request(
        result(requires_review=True),
        MODEL_VERSION,
        settings(),
        limits(),
        suggested_text="Непроменен OCR текст.",
        rejected_suggestion_stage="number_grounding",
    )

    rejection = next(
        signal
        for signal in request.assessment.signals
        if signal.signal_type == "VISION_SUGGESTION_REJECTED"
    )
    assert request.requires_review is True
    assert request.assessment.quality_status is QualityStatus.REVIEW_REQUIRED
    assert rejection.signal_type == "VISION_SUGGESTION_REJECTED"
    assert rejection.text_value == "number_grounding"
    assert "grounding safeguard" in rejection.safe_message


def test_maps_total_issue_rejection_as_completed_advisory_result() -> None:
    request = build_assessment_request(
        result(requires_review=True),
        MODEL_VERSION,
        settings(),
        limits(),
        suggested_text="Непроменен OCR текст.",
        outcome=VisionResultOutcome.PROPOSED_TEXT_REJECTED,
        rejected_suggestion_stage="issue_excerpt",
        rejected_issue_stages=("issue_excerpt", "issue_offsets"),
    )

    rejection = next(
        signal for signal in request.assessment.signals
        if signal.signal_type == "VISION_ISSUES_REJECTED"
    )
    assert request.issues == ()
    assert request.requires_review is True
    assert request.suggested_text == "Непроменен OCR текст."
    assert json.loads(rejection.text_value) == {
        "count": 2,
        "stages": ["issue_excerpt", "issue_offsets"],
    }


def test_maps_bounded_subset_from_real_size_ollama_response() -> None:
    model_issues = []
    excerpts = []
    for index in range(12):
        excerpt = f"Откъс {index}: " + ("видим български текст " * 4)
        excerpts.append(excerpt)
        model_issues.append({
            "type": "OCR_GARBAGE",
            "excerpt": excerpt,
            "suggestedReplacement": None,
            "reason": "Вероятна грешка при разпознаването.",
            "confidence": round(0.40 + index * 0.04, 2),
        })
    model_result = result(issues=model_issues)
    assert 4_500 <= len(
        model_result.model_dump_json(by_alias=True).encode("utf-8")
    ) <= 5_500

    prepared = prepare_model_result(
        model_result,
        "\n".join(excerpts),
        limits(),
    )
    request = build_assessment_request(
        prepared.result,
        MODEL_VERSION,
        settings(),
        limits(),
        suggested_text=prepared.suggested_text,
        outcome=prepared.outcome,
        rejected_suggestion_stage=prepared.failed_validation_rule,
        rejected_issue_stages=prepared.rejected_issue_stages,
    )

    issues_json = json.dumps(
        [issue.model_dump(by_alias=True, mode="json") for issue in request.issues],
        ensure_ascii=False,
        separators=(",", ":"),
    )
    assert len(issues_json) <= 4_000
    assert request.requires_review is True
    assert any(
        signal.signal_type == "VISION_ISSUES_REJECTED"
        for signal in request.assessment.signals
    )


def test_maps_bounded_diagnostic_metadata() -> None:
    request = build_assessment_request(
        result(),
        MODEL_VERSION,
        settings(),
        limits(),
        suggested_text="Непроменен OCR текст.",
        diagnostics=OllamaDiagnostics(
            done_reason="stop",
            prompt_tokens=100,
            output_tokens=20,
            load_duration_ns=1,
            prompt_evaluation_duration_ns=2,
            generation_duration_ns=3,
            response_bytes=400,
        ),
        compact_retry_used=True,
    )

    diagnostic = next(
        signal
        for signal in request.assessment.signals
        if signal.signal_type == "VISION_MODEL_DIAGNOSTICS"
    )
    value = json.loads(diagnostic.text_value)
    assert value["doneReason"] == "stop"
    assert value["promptTokens"] == 100
    assert value["outputTokens"] == 20
    assert value["compactRetryUsed"] is True


def test_rejects_too_many_issues() -> None:
    bounded = limits().model_copy(update={"maximum_vision_issues": 1})

    with pytest.raises(WorkerApiContractError, match="too many issues"):
        build_assessment_request(
            result(issues=[issue(), issue()]),
            MODEL_VERSION,
            settings(),
            bounded,
            suggested_text="Непроменен OCR текст.",
        )


@pytest.mark.parametrize(
    ("field", "maximum", "message"),
    [
        ("type", "maximum_vision_issue_code_characters", "issue type"),
        ("excerpt", "maximum_vision_excerpt_characters", "issue excerpt"),
        ("reason", "maximum_vision_reason_characters", "issue reason"),
    ],
)
def test_rejects_oversized_issue_fields(
    field: str,
    maximum: str,
    message: str,
) -> None:
    bounded = limits().model_copy(update={maximum: 3})
    oversized = issue()
    oversized[field] = "four"

    with pytest.raises(WorkerApiContractError, match=message):
        build_assessment_request(
            result(issues=[oversized]),
            MODEL_VERSION,
            settings(),
            bounded,
            suggested_text="Непроменен OCR текст.",
        )


def test_rejects_oversized_model_version_using_its_own_limit() -> None:
    bounded = limits().model_copy(
        update={
            "maximum_vision_model_version_characters": 10,
            "maximum_vision_prompt_version_characters": 100,
        }
    )

    with pytest.raises(WorkerApiContractError, match="model version"):
        build_assessment_request(
            result(),
            MODEL_VERSION,
            settings(),
            bounded,
            suggested_text="Непроменен OCR текст.",
        )


def test_rejects_oversized_serialized_payload() -> None:
    bounded = limits().model_copy(
        update={"maximum_vision_assessment_payload_bytes": 10}
    )

    with pytest.raises(WorkerApiContractError, match="payload limit"):
        build_assessment_request(
            result(),
            MODEL_VERSION,
            settings(),
            bounded,
            suggested_text="Непроменен OCR текст.",
        )


def test_rejects_oversized_stored_issues_json() -> None:
    bounded = limits().model_copy(
        update={"maximum_vision_issues_json_characters": 10}
    )

    with pytest.raises(WorkerApiContractError, match="stored JSON limit"):
        build_assessment_request(
            result(issues=[issue()]),
            MODEL_VERSION,
            settings(),
            bounded,
            suggested_text="Непроменен OCR текст.",
        )
