import json

from ethnowear_worker_common.api.errors import WorkerApiContractError
from ethnowear_worker_common.api.models import ResourceLimits

from ethnowear_vision_worker.api.models import (
    QualityAssessmentRequest,
    QualitySignalRequest,
    QualitySignalSeverity,
    QualityStatus,
    VisionAssessmentRequest,
    VisionIssueRequest,
    VisionUncertainPassageRequest
)
from ethnowear_vision_worker.config import VisionWorkerSettings
from ethnowear_vision_worker.ollama.models import VisionModelResult
from ethnowear_vision_worker.ollama.models import OllamaDiagnostics
from ethnowear_vision_worker.assessment.validation import VisionResultOutcome


def build_assessment_request(
        result: VisionModelResult,
        model_version: str,
        settings: VisionWorkerSettings,
        limits: ResourceLimits,
        suggested_text: str,
        outcome: VisionResultOutcome = VisionResultOutcome.NO_DISCREPANCY,
        diagnostics: OllamaDiagnostics | None = None,
        compact_retry_used: bool = False,
        rejected_suggestion_stage: str | None = None,
        rejected_issue_stages: tuple[str, ...] = (),
) -> VisionAssessmentRequest:
    _required_with_limit(
        settings.vision_model,
        limits.maximum_vision_model_name_characters,
        "Vision model name"
    )
    _required_with_limit(
        model_version,
        limits.maximum_vision_model_version_characters,
        "Vision model version"
    )
    _required_with_limit(
        settings.prompt_version,
        limits.maximum_vision_prompt_version_characters,
        "Vision prompt version"
    )
    _required_with_limit(
        suggested_text,
        limits.maximum_vision_suggestion_characters,
        "Vision suggested text"
    )

    if len(result.issues) > limits.maximum_vision_issues:
        raise WorkerApiContractError("Vision result contains too many issues")

    if len(result.uncertain_passages) > limits.maximum_vision_uncertain_passages:
        raise WorkerApiContractError("Vision result contains too many uncertain passages")

    issues = tuple(
        _map_issue(issue, limits)
        for issue in result.issues
    )
    uncertain_passages = tuple(
        _map_uncertain_passage(passage, limits)
        for passage in result.uncertain_passages
    )

    requires_review = (
        result.requires_review
        or bool(issues)
        or bool(uncertain_passages)
    )

    score = round(result.score, 4)

    signals = [QualitySignalRequest(
        type="VISION_MODEL_SCORE",
        decimal_value=score,
        severity=(
            QualitySignalSeverity.WARNING
            if requires_review
            else QualitySignalSeverity.INFO
        ),
        weight=1.0,
        safe_message=(
            "Vision comparison requires human review"
            if requires_review
            else "Vision comparison found no explicit discrepancy"
        )
    )]

    signals.append(
        QualitySignalRequest(
            type="VISION_RESULT_OUTCOME",
            text_value=outcome,
            severity=(
                QualitySignalSeverity.WARNING
                if outcome is VisionResultOutcome.PROPOSED_TEXT_REJECTED
                else QualitySignalSeverity.INFO
            ),
            weight=1.0,
            safe_message="Vision result handling outcome",
        )
    )

    if diagnostics is not None:
        diagnostic_value = json.dumps(
            {
                "doneReason": diagnostics.done_reason,
                "promptTokens": diagnostics.prompt_tokens,
                "outputTokens": diagnostics.output_tokens,
                "loadDurationNs": diagnostics.load_duration_ns,
                "promptEvaluationDurationNs": diagnostics.prompt_evaluation_duration_ns,
                "generationDurationNs": diagnostics.generation_duration_ns,
                "responseBytes": diagnostics.response_bytes,
                "compactRetryUsed": compact_retry_used,
            },
            separators=(",", ":"),
        )
        _optional_with_limit(
            diagnostic_value,
            limits.maximum_quality_signal_text_characters,
            "Vision diagnostic metadata",
        )
        signals.append(
            QualitySignalRequest(
                type="VISION_MODEL_DIAGNOSTICS",
                text_value=diagnostic_value,
                severity=QualitySignalSeverity.INFO,
                weight=1.0,
                safe_message="Bounded vision model execution diagnostics",
            )
        )

    if rejected_suggestion_stage is not None:
        signals.append(
            QualitySignalRequest(
                type="VISION_SUGGESTION_REJECTED",
                text_value=rejected_suggestion_stage,
                severity=QualitySignalSeverity.WARNING,
                weight=1.0,
                safe_message="Vision suggestion failed a grounding safeguard",
            )
        )

    if rejected_issue_stages:
        rejected_value = json.dumps(
            {
                "count": len(rejected_issue_stages),
                "stages": sorted(set(rejected_issue_stages)),
            },
            separators=(",", ":"),
        )
        _optional_with_limit(
            rejected_value,
            limits.maximum_quality_signal_text_characters,
            "Rejected vision issue metadata",
        )
        signals.append(QualitySignalRequest(
            type="VISION_ISSUES_REJECTED",
            text_value=rejected_value,
            severity=QualitySignalSeverity.WARNING,
            weight=1.0,
            safe_message="Invalid vision guidance was discarded",
        ))

    for signal in signals:
        _required_with_limit(
            signal.signal_type,
            limits.maximum_quality_signal_type_characters,
            "Vision signal type"
        )
        _optional_with_limit(
            signal.safe_message,
            limits.maximum_quality_message_characters,
            "Vision signal message"
        )

    summary = (
        "Vision comparison requires human review"
        if requires_review
        else "Vision comparison completed without an explicit discrepancy"
    )
    limitations = (
        "Advisory result only; human transcription approval remains required"
    )

    _optional_with_limit(
        summary,
        limits.maximum_quality_summary_characters,
        "Vision assessment summary"
    )
    _optional_with_limit(
        limitations,
        limits.maximum_quality_limitations_characters,
        "Vision assessment limitations"
    )

    request = VisionAssessmentRequest(
        assessment=QualityAssessmentRequest(
            assessor_name=settings.vision_model,
            assessor_version=model_version,
            score_version=settings.prompt_version,
            overall_score=score,
            quality_status=(
                QualityStatus.REVIEW_REQUIRED
                if requires_review
                else QualityStatus.PASS
            ),
            summary=summary,
            limitations=limitations,
            signals=tuple(signals)
        ),
        requires_review=requires_review,
        suggested_text=suggested_text,
        model_name=settings.vision_model,
        model_version=model_version,
        prompt_version=settings.prompt_version,
        issues=issues,
        uncertain_passages=uncertain_passages,
    )

    _validate_serialized_limits(request, limits)

    return request


def _map_issue(issue, limits: ResourceLimits) -> VisionIssueRequest:
    original_substring = issue.original_substring or issue.excerpt
    _required_with_limit(
        issue.issue_type,
        limits.maximum_vision_issue_code_characters,
        "Vision issue type"
    )
    _required_with_limit(
        issue.excerpt,
        limits.maximum_vision_excerpt_characters,
        "Vision issue excerpt"
    )
    _required_with_limit(
        original_substring,
        limits.maximum_vision_excerpt_characters,
        "Vision issue original substring"
    )
    _required_with_limit(
        issue.reason,
        limits.maximum_vision_reason_characters,
        "Vision issue reason"
    )
    _optional_with_limit(
        issue.suggested_replacement,
        limits.maximum_vision_excerpt_characters,
        "Vision issue replacement"
    )

    return VisionIssueRequest(
        issue_type=issue.issue_type,
        explanation_bg=issue.reason,
        original_text=original_substring,
        original_context=issue.excerpt,
        suggested_text=issue.suggested_replacement,
        suggested_context=_suggested_context(issue),
        start_offset=issue.start_offset,
        end_offset=issue.end_offset,
        confidence=round(issue.confidence, 4),
        safely_applicable=(
            issue.suggested_replacement is not None
            and issue.start_offset is not None
            and issue.end_offset is not None
        ),
    )


def _suggested_context(issue) -> str | None:
    if issue.suggested_replacement is None:
        return None
    original = issue.original_substring or issue.excerpt
    return issue.excerpt.replace(original, issue.suggested_replacement, 1)


def _map_uncertain_passage(passage, limits: ResourceLimits) -> VisionUncertainPassageRequest:
    _required_with_limit(
        passage.excerpt,
        limits.maximum_vision_excerpt_characters,
        "Uncertain passage excerpt"
    )
    _required_with_limit(
        passage.reason,
        limits.maximum_vision_reason_characters,
        "Uncertain passage reason"
    )

    return VisionUncertainPassageRequest(
        excerpt=passage.excerpt,
        reason=passage.reason,
        confidence=round(passage.confidence, 4),
    )


def _validate_serialized_limits(
        request: VisionAssessmentRequest,
        limits: ResourceLimits
) -> None:
    payload = request.model_dump_json(by_alias=True).encode("utf-8")

    if len(payload) > limits.maximum_vision_assessment_payload_bytes:
        raise WorkerApiContractError("Vision assessment exceeds the payload limit")

    issues_json = json.dumps(
        [
            issue.model_dump(by_alias=True, mode="json")
            for issue in request.issues
        ],
        ensure_ascii=False,
        separators=(",", ":"),
    )

    if len(issues_json) > limits.maximum_vision_issues_json_characters:
        raise WorkerApiContractError("Vision issues exceed the stored JSON limit")


def _required_with_limit(value: str, maximum_characters: int, field_name: str) -> None:
    if not value.strip():
        raise WorkerApiContractError(f"{field_name} must not be blank")

    if len(value) > maximum_characters:
        raise WorkerApiContractError(f"{field_name} exceeds the configured limit")


def _optional_with_limit(value: str | None, maximum_characters: int, field_name: str) -> None:
    if value is not None and len(value) > maximum_characters:
        raise WorkerApiContractError(f"{field_name} exceeds the configured limit")
