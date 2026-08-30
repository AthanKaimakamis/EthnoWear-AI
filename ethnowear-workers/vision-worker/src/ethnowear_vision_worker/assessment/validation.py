import json
import re
import unicodedata
from difflib import SequenceMatcher
from dataclasses import dataclass
from enum import StrEnum

from ethnowear_vision_worker.errors import VisionContractError
from ethnowear_vision_worker.ollama.models import VisionModelResult
from ethnowear_worker_common.api.models import ResourceLimits

WORD_PATTERN = re.compile(r"[^\W_]+", re.UNICODE)
NUMBER_PATTERN = re.compile(r"\d+(?:[.,]\d+)?")
CYRILLIC_PATTERN = re.compile(r"[А-Яа-яЀ-ӿ]")
CORRECTION_IN_REASON_PATTERN = re.compile(
    r"['„“‘]([^'„“’]+)['“”’]\s+трябва\s+да\s+е\s+"
    r"['„“‘]([^'„“’]+)['“”’]",
    re.IGNORECASE,
)
ROMAN_CORRECTION_IN_REASON_PATTERN = re.compile(
    r"вижда\s+като\s+([IVXLCDM]+(?:[-–—]+[IVXLCDM]+)?),\s*"
    r"но\s+в\s+оригинала\s+е\s+([А-ЯІI1VX]+(?:[-–—]+[А-ЯІI1VX]+)?)",
    re.IGNORECASE,
)
IS_NOT_CORRECTION_PATTERN = re.compile(
    r"\bе\s+[„“\"]?([^,\s„“\"]+)[„“\"]?\s*,\s*а\s+не\s+"
    r"[„“\"]?([^\s.„“\"]+)[„“\"]?",
    re.IGNORECASE,
)
CORRECT_QUOTED_REPLACEMENT_PATTERN = re.compile(
    r"\bправилното\s+е\s+[\"„“]([^\"„”]+)[\"“”]",
    re.IGNORECASE,
)
CORRECTION_TOKEN = r"[0-9A-Za-zА-Яа-яІі]+(?:[-–—]+[0-9A-Za-zА-Яа-яІі]+)*"
UNQUOTED_CORRECTION_PATTERN = re.compile(
    rf"(?P<original>{CORRECTION_TOKEN})\s+трябва\s+да\s+е\s+"
    rf"(?P<replacement>{CORRECTION_TOKEN})",
    re.IGNORECASE,
)
PAIRED_CORRECTION_PATTERN = re.compile(
    rf"(?P<original1>{CORRECTION_TOKEN})\s+и\s+"
    rf"(?P<original2>{CORRECTION_TOKEN})\s+трябва\s+да\s+са\s+"
    rf"(?P<replacement1>{CORRECTION_TOKEN})\s+и\s+"
    rf"(?P<replacement2>{CORRECTION_TOKEN})",
    re.IGNORECASE,
)

MINIMUM_OVERLAP_WORDS = 8
MINIMUM_TOKEN_OVERLAP_RATIO = 0.20
MAXIMUM_EXPANSION_RATIO = 2.0
MAXIMUM_EXPANSION_CHARACTERS = 500
MINIMUM_RESCUE_LENGTH_RATIO = 0.35
MAXIMUM_RESCUE_LENGTH_RATIO = 1.65
MINIMUM_RESCUE_CHANGE_RATIO = 0.005


class VisionResultOutcome(StrEnum):
    ACCEPTED_PROPOSED_CORRECTION = "ACCEPTED_PROPOSED_CORRECTION"
    ISSUES_ONLY = "ISSUES_ONLY"
    PROPOSED_TEXT_REJECTED = "PROPOSED_TEXT_REJECTED"
    NO_DISCREPANCY = "NO_DISCREPANCY"


@dataclass(frozen=True, slots=True)
class PreparedVisionResult:
    result: VisionModelResult
    suggested_text: str
    outcome: VisionResultOutcome
    failed_validation_rule: str | None
    rejected_issue_stages: tuple[str, ...] = ()


def validate_model_result(result: VisionModelResult, raw_ocr_text: str) -> None:
    _validate_issue_excerpts(result, raw_ocr_text)
    _validate_uncertain_passages(result, raw_ocr_text)

    for issue in result.issues:
        _validate_control_characters(issue.excerpt)
        _validate_control_characters(issue.reason)

        if issue.suggested_replacement is not None:
            _validate_control_characters(issue.suggested_replacement)
            _validate_expansion(issue.suggested_replacement, issue.excerpt)
            _validate_token_overlap(issue.suggested_replacement, issue.excerpt)
            _validate_numbers(issue.suggested_replacement, issue.excerpt)


def prepare_model_result(
        result: VisionModelResult,
        raw_ocr_text: str,
        limits: ResourceLimits | None = None,
        *,
        allow_rescue_transcription: bool = True,
) -> PreparedVisionResult:
    issue_candidates = []
    valid_uncertain_passages = []
    replacements: list[tuple[str, str]] = []
    failed_rule = None
    rejected_issue_stages: list[str] = []

    for issue_index, issue in enumerate(result.issues):
        issue = _recover_structured_replacement(issue, raw_ocr_text)
        if _is_noop_issue(issue):
            rejected_issue_stages.append("no_op_issue")
            failed_rule = failed_rule or "no_op_issue"
            continue
        try:
            _validate_issue_excerpt(issue.excerpt, raw_ocr_text)
            issue = _resolve_issue_location(issue, raw_ocr_text)
            if limits is not None:
                _validate_issue_limits(issue, limits)
            _validate_actionable_context(issue)
            _validate_control_characters(issue.excerpt)
            _validate_control_characters(issue.reason)
            _validate_bulgarian_reason(issue.reason)
        except VisionContractError as error:
            original = issue.original_substring or issue.excerpt
            if (
                error.stage == "issue_context"
                and issue.suggested_replacement is not None
                and original in issue.excerpt
                and raw_ocr_text.count(original) > 1
            ):
                # Preserve the model's grounded warning for a human, but never
                # apply a correction when the short OCR token is ambiguous.
                rejected_issue_stages.append("ambiguous_issue_location")
                issue = issue.model_copy(update={
                    "suggested_replacement": None,
                    "start_offset": None,
                    "end_offset": None,
                })
            else:
                failed_rule = failed_rule or error.stage
                rejected_issue_stages.append(error.stage)
                continue

        replacement = issue.suggested_replacement

        if replacement is not None:
            try:
                _validate_control_characters(replacement)
                original = issue.original_substring or issue.excerpt
                _validate_expansion(replacement, original)
                _validate_token_overlap(replacement, original)
                _validate_actionable_numbers(issue, replacement, original)
                _validate_wrapped_prefix_duplication(issue, replacement, raw_ocr_text)
            except VisionContractError as error:
                failed_rule = failed_rule or error.stage
                rejected_issue_stages.append(error.stage)
                issue = issue.model_copy(update={"suggested_replacement": None})
        if _duplicates_existing_issue(issue, issue_candidates):
            rejected_issue_stages.append("duplicate_issue")
            failed_rule = failed_rule or "duplicate_issue"
            continue
        issue_candidates.append((issue_index, issue))

    valid_issues = _select_issues_within_budget(
        issue_candidates,
        limits,
        rejected_issue_stages,
    )
    if len(valid_issues) != len(issue_candidates):
        failed_rule = failed_rule or "issues_json_limit"

    # Discard invalid model items independently. If at least one grounded,
    # actionable correction remains, it is still safe and useful for review.
    if any(issue.suggested_replacement is not None for issue in valid_issues):
        failed_rule = None

    for issue in valid_issues:
        if issue.suggested_replacement is not None:
            replacements.append((
                issue.original_substring or issue.excerpt,
                issue.suggested_replacement,
                issue.start_offset,
                issue.end_offset,
            ))

    for passage in result.uncertain_passages:
        try:
            _validate_issue_excerpt(passage.excerpt, raw_ocr_text)
            _validate_control_characters(passage.excerpt)
            _validate_control_characters(passage.reason)
        except VisionContractError as error:
            failed_rule = failed_rule or error.stage
            rejected_issue_stages.append(error.stage)
            continue

        valid_uncertain_passages.append(passage)

    suggested_text = raw_ocr_text
    rescue_accepted = False

    if result.suggested_text is not None:
        if not allow_rescue_transcription:
            failed_rule = failed_rule or "unexpected_rescue_transcription"
            rejected_issue_stages.append("unexpected_rescue_transcription")
        else:
            try:
                _validate_rescue_transcription(result.suggested_text, raw_ocr_text, limits)
            except VisionContractError as error:
                failed_rule = failed_rule or error.stage
                rejected_issue_stages.append(error.stage)
            else:
                suggested_text = result.suggested_text
                rescue_accepted = suggested_text != raw_ocr_text

    for original, replacement, start, end in (() if rescue_accepted else sorted(
            replacements,
            key=lambda item: item[2] if item[2] is not None else -1,
            reverse=True,
    )):
        if start is not None and end is not None:
            suggested_text = (
                suggested_text[:start]
                + replacement
                + suggested_text[end:]
            )
        else:
            suggested_text = suggested_text.replace(original, replacement, 1)

    prepared_result = result.model_copy(
        update={
            "requires_review": (
                result.requires_review
                or bool(valid_issues)
                or bool(valid_uncertain_passages)
                or failed_rule is not None
            ),
            "issues": tuple(valid_issues),
            "uncertain_passages": tuple(valid_uncertain_passages),
        }
    )

    if failed_rule is not None:
        outcome = VisionResultOutcome.PROPOSED_TEXT_REJECTED
    elif rescue_accepted or replacements:
        outcome = VisionResultOutcome.ACCEPTED_PROPOSED_CORRECTION
    elif valid_issues or valid_uncertain_passages:
        outcome = VisionResultOutcome.ISSUES_ONLY
    else:
        outcome = VisionResultOutcome.NO_DISCREPANCY

    return PreparedVisionResult(
        result=prepared_result,
        suggested_text=suggested_text,
        outcome=outcome,
        failed_validation_rule=failed_rule,
        rejected_issue_stages=tuple(rejected_issue_stages),
    )


def _validate_rescue_transcription(
        suggested_text: str,
        raw_ocr_text: str,
        limits: ResourceLimits | None,
) -> None:
    _validate_control_characters(suggested_text)
    if suggested_text.strip() == raw_ocr_text.strip():
        raise VisionContractError("no_op_suggestion", "Vision rescue text is unchanged")
    if limits is not None and len(suggested_text) > limits.maximum_vision_suggestion_characters:
        raise VisionContractError(
            "suggestion_size",
            "Vision rescue transcription exceeds the backend limit",
        )

    source_length = max(len(raw_ocr_text.strip()), 1)
    ratio = len(suggested_text.strip()) / source_length
    if not MINIMUM_RESCUE_LENGTH_RATIO <= ratio <= MAXIMUM_RESCUE_LENGTH_RATIO:
        raise VisionContractError(
            "rescue_length",
            "Vision rescue transcription has an implausible length",
        )
    change_ratio = 1.0 - SequenceMatcher(
        None,
        raw_ocr_text.strip(),
        suggested_text.strip(),
        autojunk=False,
    ).ratio()
    if change_ratio < MINIMUM_RESCUE_CHANGE_RATIO:
        raise VisionContractError(
            "rescue_near_copy",
            "Vision rescue transcription is only a near-copy of the OCR",
        )
    _validate_token_overlap(suggested_text, raw_ocr_text)


def _recover_structured_replacement(issue, raw_ocr_text: str):
    """Recover a grounded replacement when a small model put it in the reason.

    This is deliberately narrow: both quoted values must be present in the
    Bulgarian "X трябва да е Y" form and the quoted source must occur
    inside the exact OCR excerpt. All normal grounding checks still run later.
    """
    if issue.suggested_replacement is not None:
        return issue

    recovered_pairs = _correction_pairs_from_reason(issue.reason)
    if recovered_pairs:
        recovered = _recover_from_pairs(issue, raw_ocr_text, recovered_pairs)
        if recovered is not None:
            return recovered

    match = CORRECTION_IN_REASON_PATTERN.search(issue.reason)
    if match is not None:
        original, replacement = (value.strip() for value in match.groups())
    else:
        roman_match = ROMAN_CORRECTION_IN_REASON_PATTERN.search(issue.reason)
        if roman_match is not None:
            replacement, original = (value.strip() for value in roman_match.groups())
        else:
            is_not_match = IS_NOT_CORRECTION_PATTERN.search(issue.reason)
            if is_not_match is not None:
                replacement, original = (
                    value.strip() for value in is_not_match.groups()
                )
            else:
                quoted_match = CORRECT_QUOTED_REPLACEMENT_PATTERN.search(
                    issue.reason
                )
                if quoted_match is None:
                    return issue
                original = issue.excerpt
                replacement = quoted_match.group(1).strip()
    if not original or not replacement or original == replacement:
        return issue
    if original not in issue.excerpt or original not in raw_ocr_text:
        return issue

    recovered_type = issue.issue_type
    if (
        IS_NOT_CORRECTION_PATTERN.search(issue.reason) is not None
        and "страниц" in issue.reason.casefold()
    ):
        recovered_type = "PAGE_NUMBER"

    return issue.model_copy(update={
        "issue_type": recovered_type,
        "original_substring": original,
        "suggested_replacement": replacement,
        "start_offset": None,
        "end_offset": None,
    })


def _correction_pairs_from_reason(reason: str) -> tuple[tuple[str, str], ...]:
    paired = PAIRED_CORRECTION_PATTERN.search(reason)
    if paired is not None:
        return (
            (paired.group("original1"), paired.group("replacement1")),
            (paired.group("original2"), paired.group("replacement2")),
        )

    pairs = tuple(
        (match.group("original"), match.group("replacement"))
        for match in UNQUOTED_CORRECTION_PATTERN.finditer(reason)
    )
    return pairs[:2]


def _recover_from_pairs(issue, raw_ocr_text: str, pairs):
    """Build one minimal grounded replacement from one or two token pairs."""
    excerpt = issue.excerpt
    cursor = 0
    located = []

    for original, replacement in pairs:
        if not original or not replacement or original == replacement:
            return None
        start = excerpt.find(original, cursor)
        if start < 0 or raw_ocr_text.count(original) != 1:
            return None
        end = start + len(original)
        located.append((start, end, original, replacement))
        cursor = end

    span_start = located[0][0]
    span_end = located[-1][1]
    original_span = excerpt[span_start:span_end]
    replacement_span = original_span
    for _, _, original, replacement in located:
        replacement_span = replacement_span.replace(original, replacement, 1)

    if original_span == replacement_span or raw_ocr_text.count(original_span) != 1:
        return None

    return issue.model_copy(update={
        "original_substring": original_span,
        "suggested_replacement": replacement_span,
        "start_offset": None,
        "end_offset": None,
    })


def _is_noop_issue(issue) -> bool:
    replacement = issue.suggested_replacement
    if replacement is not None:
        original = issue.original_substring or issue.excerpt
        return replacement.strip() == original.strip()

    match = CORRECTION_IN_REASON_PATTERN.search(issue.reason)
    if match:
        return match.group(1).strip() == match.group(2).strip()
    roman_match = ROMAN_CORRECTION_IN_REASON_PATTERN.search(issue.reason)
    return bool(
        roman_match
        and roman_match.group(1).strip() == roman_match.group(2).strip()
    )


def _duplicates_existing_issue(issue, candidates: list[tuple[int, object]]) -> bool:
    original = issue.original_substring or issue.excerpt
    replacement = issue.suggested_replacement
    for _, existing in candidates:
        existing_original = existing.original_substring or existing.excerpt
        if original == existing_original and replacement == existing.suggested_replacement:
            return True
        if (replacement is None and existing.suggested_replacement is None
                and (original in existing_original or existing_original in original)):
            return True
    return False


def _validate_issue_limits(issue, limits: ResourceLimits) -> None:
    values = (
        (issue.issue_type, limits.maximum_vision_issue_code_characters,
         "issue_type"),
        (issue.excerpt, limits.maximum_vision_excerpt_characters,
         "issue_excerpt_size"),
        (issue.original_substring, limits.maximum_vision_excerpt_characters,
         "issue_original_size"),
        (issue.suggested_replacement, limits.maximum_vision_excerpt_characters,
         "issue_replacement_size"),
        (issue.reason, limits.maximum_vision_reason_characters,
         "issue_reason_size"),
    )
    for value, maximum, stage in values:
        if value is not None and len(value) > maximum:
            raise VisionContractError(stage, "Vision issue field exceeds the backend limit")


def _select_issues_within_budget(
        candidates,
        limits: ResourceLimits | None,
        rejected_issue_stages: list[str],
):
    if limits is None:
        return [issue for _, issue in candidates]

    selected = []
    ranked = sorted(
        candidates,
        key=lambda candidate: (-candidate[1].confidence, candidate[0]),
    )
    for original_index, issue in ranked:
        if len(selected) >= limits.maximum_vision_issues:
            rejected_issue_stages.append("issues_json_limit")
            continue

        proposed = [selected_issue for _, selected_issue in selected] + [issue]
        if _spring_issues_json_length(proposed) > limits.maximum_vision_issues_json_characters:
            rejected_issue_stages.append("issues_json_limit")
            continue
        selected.append((original_index, issue))

    return [issue for _, issue in sorted(selected, key=lambda item: item[0])]


def _spring_issues_json_length(issues) -> int:
    payload = [_spring_issue_payload(issue) for issue in issues]
    return len(json.dumps(
        payload,
        ensure_ascii=False,
        separators=(",", ":"),
    ))


def _spring_issue_payload(issue) -> dict[str, object]:
    original = issue.original_substring or issue.excerpt
    replacement = issue.suggested_replacement
    suggested_context = (
        issue.excerpt.replace(original, replacement, 1)
        if replacement is not None
        else None
    )
    safely_applicable = (
        replacement is not None
        and issue.start_offset is not None
        and issue.end_offset is not None
    )
    return {
        "issueType": issue.issue_type,
        "explanationBg": issue.reason,
        "confidence": round(issue.confidence, 4),
        "originalText": original,
        "originalContext": issue.excerpt,
        "suggestedText": replacement,
        "suggestedContext": suggested_context,
        "startOffset": issue.start_offset,
        "endOffset": issue.end_offset,
        "safelyApplicable": safely_applicable,
    }


def _validate_control_characters(text: str) -> None:
    for character in text:
        if character in {"\n", "\r", "\t"}:
            continue

        if unicodedata.category(character) == "Cc":
            raise VisionContractError(
                "control_characters",
                "Vision suggestion contains unsupported control characters",
            )


def _resolve_issue_location(issue, raw_ocr_text: str):
    original = issue.original_substring or issue.excerpt

    if original not in issue.excerpt:
        raise VisionContractError(
            "issue_context",
            "Vision issue matching text is absent from its context excerpt",
        )

    start = issue.start_offset
    end = issue.end_offset

    # Model-provided offsets are advisory. Discard unreliable values and derive
    # exact offsets from the authoritative OCR string whenever it is unique.
    if start is not None and end is not None:
        if end <= start or end > len(raw_ocr_text) or raw_ocr_text[start:end] != original:
            start = None
            end = None
    elif start is not None or end is not None:
        start = None
        end = None

    if start is None and end is None and raw_ocr_text.count(original) == 1:
        start = raw_ocr_text.index(original)
        end = start + len(original)

    return issue.model_copy(update={
        "original_substring": original,
        "start_offset": start,
        "end_offset": end,
    })


def _validate_actionable_context(issue) -> None:
    if issue.suggested_replacement is None:
        return

    context_tokens = WORD_PATTERN.findall(issue.excerpt)
    original = issue.original_substring or issue.excerpt

    if (
        issue.start_offset is not None
        and issue.end_offset is not None
        and re.fullmatch(r"[IVXLCDM]+(?:[-–—]+[IVXLCDM]+)?", issue.suggested_replacement)
        and re.fullmatch(r"[А-ЯІI1VX]+(?:[-–—]+[А-ЯІI1VX]+)?", original)
    ):
        return

    if issue.excerpt == original and len(context_tokens) < 3:
        raise VisionContractError(
            "issue_context",
            "Vision correction lacks enough surrounding OCR context",
        )


def _validate_wrapped_prefix_duplication(issue, replacement: str, raw_ocr_text: str) -> None:
    """Reject a replacement that repeats a prefix from a hyphenated prior line."""
    start = issue.start_offset
    if start is None:
        return
    match = re.search(
        r"([^\W\d_]{2,})-\s*\n\s*$",
        raw_ocr_text[:start],
        re.UNICODE,
    )
    if match is None:
        return
    prefix = match.group(1)
    original = issue.original_substring or issue.excerpt
    if replacement.casefold().startswith(prefix.casefold()) \
            and not original.casefold().startswith(prefix.casefold()):
        raise VisionContractError(
            "wrapped_prefix_duplication",
            "Vision replacement duplicates a prefix from the previous wrapped line",
        )


def _validate_bulgarian_reason(reason: str) -> None:
    if not CYRILLIC_PATTERN.search(reason):
        raise VisionContractError(
            "issue_language",
            "Vision issue explanation must be in Bulgarian",
        )


def _validate_expansion(suggested_text: str, raw_ocr_text: str) -> None:
    source_length = len(raw_ocr_text.strip())
    suggested_length = len(suggested_text.strip())

    allowed_length = max(
        int(source_length * MAXIMUM_EXPANSION_RATIO),
        source_length + MAXIMUM_EXPANSION_CHARACTERS,
    )

    if suggested_length > allowed_length:
        raise VisionContractError(
            "suggestion_expansion",
            "Vision suggestion expands the OCR text excessively",
        )


def _validate_token_overlap(suggested_text: str, raw_ocr_text: str) -> None:
    source_tokens = _token(raw_ocr_text)
    suggested_tokens = _token(suggested_text)

    if len(source_tokens) < MINIMUM_OVERLAP_WORDS or len(suggested_tokens) < MINIMUM_OVERLAP_WORDS:
        return

    overlapping_tokens = source_tokens & suggested_tokens
    overlap_ratio = len(overlapping_tokens) / len(suggested_tokens)

    if overlap_ratio < MINIMUM_TOKEN_OVERLAP_RATIO:
        raise VisionContractError(
            "token_overlap",
            "Vision suggestion is insufficiently grounded in the OCR text",
        )


def _validate_issue_excerpts(result: VisionModelResult, raw_ocr_text: str) -> None:
    for issue in result.issues:
        _validate_issue_excerpt(issue.excerpt, raw_ocr_text)


def _validate_uncertain_passages(
    result: VisionModelResult,
    raw_ocr_text: str,
) -> None:
    for passage in result.uncertain_passages:
        _validate_issue_excerpt(passage.excerpt, raw_ocr_text)


def _validate_issue_excerpt(excerpt: str, raw_ocr_text: str) -> None:
    if excerpt not in raw_ocr_text:
        raise VisionContractError(
            "issue_excerpt",
            "Vision issue references text absent from the OCR result",
        )


def _validate_numbers(suggested_text: str, source_text: str) -> None:
    source_numbers = set(NUMBER_PATTERN.findall(source_text))
    suggested_numbers = set(NUMBER_PATTERN.findall(suggested_text))

    added_numbers = suggested_numbers - source_numbers

    if _is_roman_range_correction(source_text, suggested_text):
        # A common OCR error turns Roman I into digit 1. Permit removing only
        # that digit when both sides contain an explicit numeral range and all
        # other Arabic numbers remain unchanged.
        if not added_numbers and source_numbers - suggested_numbers <= {"1"}:
            return

    if added_numbers:
        raise VisionContractError(
            "number_grounding",
            "Vision suggestion introduces unsupported numeric values",
        )

    if source_numbers - suggested_numbers:
        raise VisionContractError(
            "number_grounding",
            "Vision suggestion removes numeric values",
        )


def _validate_actionable_numbers(issue, suggested_text: str, source_text: str) -> None:
    if (
        issue.issue_type == "PAGE_NUMBER"
        and re.fullmatch(r"\d{1,4}", source_text.strip())
        and re.fullmatch(r"\d{1,4}", suggested_text.strip())
    ):
        # The model compared this exact local page-number glyph with the image.
        # It remains an unapplied suggestion requiring human review.
        return
    _validate_numbers(suggested_text, source_text)


def _is_roman_range_correction(source_text: str, suggested_text: str) -> bool:
    corrupted = re.search(
        r"[ПУШХТІИI1VX]+[-–—]{1,2}[ПУШХТІИI1VX]+",
        source_text,
        re.IGNORECASE,
    )
    corrected = re.search(
        r"\b[IVXLCDM]+[-–—]{1,2}[IVXLCDM]+\b",
        suggested_text,
        re.IGNORECASE,
    )
    return corrupted is not None and corrected is not None



def _token(text: str) -> set[str]:
    return {
        token.casefold()
        for token in WORD_PATTERN.findall(text)
        if len(token) > 1
    }


def _normalize(text: str) -> str:
    return " ".join(text.casefold().split())
