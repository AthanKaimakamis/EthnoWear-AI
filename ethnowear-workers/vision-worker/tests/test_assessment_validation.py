from types import SimpleNamespace

import pytest

from ethnowear_worker_common.api.errors import WorkerApiContractError
from ethnowear_vision_worker.assessment.validation import (
    VisionResultOutcome,
    _spring_issues_json_length,
    prepare_model_result,
    validate_model_result,
)
from ethnowear_vision_worker.ollama.models import VisionModelResult


SOURCE_TEXT = " ".join(
    [
        "българската",
        "народна",
        "везба",
        "съдържа",
        "геометрични",
        "орнаменти",
        "растителни",
        "мотиви",
        "традиционни",
        "цветове",
        "регионални",
        "техники",
        "културни",
        "значения",
        "исторически",
        "източници",
        "местни",
        "наименования",
        "ръчни",
        "изработки",
        "символи",
        "обичаи",
        "носии",
        "тъкани",
    ]
)


def result(
    *,
    issues: list[dict[str, object]] | None = None,
    uncertain_passages: list[dict[str, object]] | None = None,
) -> VisionModelResult:
    return VisionModelResult.model_validate(
        {
            "score": 0.7,
            "requiresReview": True,
            "issues": issues or [],
            "uncertainPassages": uncertain_passages or [],
        }
    )


def issue(
    excerpt: str,
    suggested_replacement: str | None = None,
    *,
    original_substring: str | None = None,
) -> dict[str, object]:
    value = {
        "type": "OCR_GARBAGE",
        "excerpt": excerpt,
        "suggestedReplacement": suggested_replacement,
        "reason": "Откъсът съдържа вероятна OCR грешка",
        "confidence": 0.95,
    }
    if original_substring is not None:
        value["originalSubstring"] = original_substring
    return value


def test_accepts_grounded_correction() -> None:
    validate_model_result(
        result(issues=[issue("народна везба", "народната везба")]),
        SOURCE_TEXT,
    )


def test_rejects_hallucinated_replacement_paragraph() -> None:
    unrelated = " ".join(f"inventedword{index}" for index in range(24))

    with pytest.raises(WorkerApiContractError, match="insufficiently grounded"):
        validate_model_result(
            result(issues=[issue(SOURCE_TEXT, unrelated)]),
            SOURCE_TEXT,
        )


def test_rejects_excessive_text_expansion() -> None:
    expanded = "нова измислена информация " * 300

    with pytest.raises(WorkerApiContractError, match="expands.*excessively"):
        validate_model_result(
            result(issues=[issue("Кратък OCR текст.", expanded)]),
            "Кратък OCR текст.",
        )


def test_preserves_legitimate_numbers() -> None:
    source = "Страница 98 съдържа цитат от 1936 г. и обр. 14."

    validate_model_result(result(issues=[issue(source, source)]), source)


def test_rejects_introduced_numeric_value() -> None:
    source = "Страница 98 съдържа исторически цитат."
    suggestion = "Страница 98 съдържа исторически цитат от 1936 г."

    with pytest.raises(WorkerApiContractError, match="unsupported numeric"):
        validate_model_result(
            result(issues=[issue(source, suggestion)]),
            source,
        )


def test_allows_numeric_garbage_issue_without_replacement() -> None:
    source = "Основен текст. 719 1604 34 27"

    validate_model_result(
        result(issues=[issue("719 1604 34 27")]),
        source,
    )


def test_rejects_removed_number_from_replacement() -> None:
    source = "Изданието е публикувано през 1936 г."
    suggestion = "Изданието е публикувано през годината."

    with pytest.raises(WorkerApiContractError, match="removes numeric"):
        validate_model_result(
            result(issues=[issue(source, suggestion)]),
            source,
        )


def test_rejects_issue_excerpt_absent_from_ocr() -> None:
    with pytest.raises(WorkerApiContractError, match="absent from the OCR"):
        validate_model_result(
            result(issues=[issue("text not present")]),
            SOURCE_TEXT,
        )


def test_prepares_accepted_local_correction() -> None:
    prepared = prepare_model_result(
        result(issues=[issue(
            "българската народна везба съдържа",
            "народната везба",
            original_substring="народна везба",
        )]),
        SOURCE_TEXT,
    )

    assert prepared.outcome is VisionResultOutcome.ACCEPTED_PROPOSED_CORRECTION
    assert "народната везба" in prepared.suggested_text
    assert prepared.failed_validation_rule is None


def test_accepts_complete_rescue_transcription_for_human_review() -> None:
    corrupted = SOURCE_TEXT + " тегивдненциеврснощаниазварненатеваетененвсснараненесеен"
    rescued = SOURCE_TEXT.replace("народна", "народната", 1)
    model_result = result()
    model_result = model_result.model_copy(update={"suggested_text": rescued})

    prepared = prepare_model_result(model_result, corrupted)

    assert prepared.suggested_text == rescued
    assert prepared.outcome is VisionResultOutcome.ACCEPTED_PROPOSED_CORRECTION
    assert prepared.failed_validation_rule is None


def test_rejects_implausibly_short_rescue_transcription() -> None:
    model_result = result().model_copy(update={"suggested_text": "Кратък текст"})

    prepared = prepare_model_result(model_result, SOURCE_TEXT)

    assert prepared.suggested_text == SOURCE_TEXT
    assert prepared.failed_validation_rule == "rescue_length"
    assert prepared.outcome is VisionResultOutcome.PROPOSED_TEXT_REJECTED


def test_rejects_unrequested_full_page_rescue_but_keeps_local_issue() -> None:
    model_result = result(issues=[issue(
        "българската народна везба съдържа геометрични орнаменти растителни мотиви",
        "народната везба",
        original_substring="народна везба",
    )]).model_copy(update={"suggested_text": SOURCE_TEXT + " допълнение"})

    prepared = prepare_model_result(
        model_result,
        SOURCE_TEXT,
        allow_rescue_transcription=False,
    )

    assert "народната везба" in prepared.suggested_text
    assert prepared.failed_validation_rule == "unexpected_rescue_transcription"


def test_rejects_rescue_that_is_only_a_near_copy() -> None:
    long_source = SOURCE_TEXT * 10
    near_copy = long_source.replace("народна", "народната", 1)
    model_result = result().model_copy(update={"suggested_text": near_copy})

    prepared = prepare_model_result(model_result, long_source)

    assert prepared.suggested_text == long_source
    assert prepared.failed_validation_rule == "rescue_near_copy"


def test_recovers_grounded_replacement_from_bulgarian_reason() -> None:
    model_issue = issue(
        "от познатия бит можа да се установп и общата закономерност",
    )
    model_issue["reason"] = (
        "Неточност в OCR: 'установп' трябва да е 'установи'."
    )
    source = "от познатия бит можа да се установп и общата закономерност"

    prepared = prepare_model_result(result(issues=[model_issue]), source)

    assert prepared.outcome is VisionResultOutcome.ACCEPTED_PROPOSED_CORRECTION
    assert prepared.result.issues[0].original_substring == "установп"
    assert prepared.result.issues[0].suggested_replacement == "установи"
    assert "установи" in prepared.suggested_text


def test_recovers_grounded_roman_replacement_from_model_explanation() -> None:
    source = "ИССФ УШ--1Х, стр. 100."
    model_issue = issue("УШ--1Х")
    model_issue["reason"] = (
        "Визуално се вижда като VIII-IX, но в оригинала е УШ--1Х."
    )

    prepared = prepare_model_result(result(issues=[model_issue]), source)

    assert prepared.outcome is VisionResultOutcome.ACCEPTED_PROPOSED_CORRECTION
    assert prepared.result.issues[0].suggested_replacement == "VIII-IX"
    assert prepared.suggested_text == "ИССФ VIII-IX, стр. 100."


def test_recovers_unquoted_roman_range_replacement_from_reason() -> None:
    source = "съществуването през П--1У в. на н. е."
    model_issue = issue(source)
    model_issue["reason"] = "Неправилно изписано число: П--1У трябва да е II--IV."

    prepared = prepare_model_result(result(issues=[model_issue]), source)

    mapped = prepared.result.issues[0]
    assert mapped.original_substring == "П--1У"
    assert mapped.suggested_replacement == "II--IV"
    assert mapped.start_offset == source.index("П--1У")
    assert prepared.suggested_text == "съществуването през II--IV в. на н. е."


def test_recovers_paired_roman_tokens_as_one_minimal_replacement() -> None:
    source = "таблици ХХХИ--ХХХУШ) представят многоценен материал"
    model_issue = issue(source)
    model_issue["reason"] = (
        "Неправилно изписано число: ХХХИ и ХХХУШ трябва да са "
        "ХХХII и ХХХVIII."
    )

    prepared = prepare_model_result(result(issues=[model_issue]), source)

    mapped = prepared.result.issues[0]
    assert mapped.original_substring == "ХХХИ--ХХХУШ"
    assert mapped.suggested_replacement == "ХХХII--ХХХVIII"
    assert prepared.suggested_text.startswith("таблици ХХХII--ХХХVIII)")


def test_recovers_two_unquoted_replacements_inside_one_local_span() -> None:
    source = "Никополско (Петева, ИзвнЕМ УШАТХ, стр. 138, обр. 7 и 8)"
    model_issue = issue("ИзвнЕМ УШАТХ, стр. 138, обр. 7 и 8)")
    model_issue["reason"] = (
        "Неправилно изписана абревиатура: ИзвнЕМ трябва да е ИзвНМ, "
        "а УШАТХ трябва да е VIII-IX."
    )

    prepared = prepare_model_result(result(issues=[model_issue]), source)

    mapped = prepared.result.issues[0]
    assert mapped.original_substring == "ИзвнЕМ УШАТХ"
    assert mapped.suggested_replacement == "ИзвНМ VIII-IX"
    assert "ИзвНМ VIII-IX, стр. 138" in prepared.suggested_text


def test_recovers_page_number_from_bulgarian_is_not_explanation() -> None:
    source = "Българската везбена орнаментика 118"
    model_issue = issue(source)
    model_issue["reason"] = "Номерът на страницата е 113, а не 118."

    prepared = prepare_model_result(result(issues=[model_issue]), source)

    assert prepared.outcome is VisionResultOutcome.ACCEPTED_PROPOSED_CORRECTION
    assert prepared.result.issues[0].original_substring == "118"
    assert prepared.result.issues[0].suggested_replacement == "113"
    assert prepared.suggested_text.endswith("113")


def test_recovers_grounded_caption_from_bulgarian_quoted_explanation() -> None:
    source = "е Oop. 42 — Престилка от Елховско"
    model_issue = issue(source)
    model_issue["reason"] = (
        'Надписът е грешен; правилното е "Обр. 42 — Престилка от Елховско".'
    )

    prepared = prepare_model_result(result(issues=[model_issue]), source)

    assert prepared.outcome is VisionResultOutcome.ACCEPTED_PROPOSED_CORRECTION
    assert prepared.result.issues[0].original_substring == source
    assert prepared.result.issues[0].suggested_replacement.startswith("Обр. 42")


def test_preserves_ambiguous_repeated_roman_correction_as_review_only() -> None:
    source = "гл. ТУ и ИССФ ТУ"
    model_issue = issue("ТУ")
    model_issue["reason"] = "Визуално се вижда като IV, но в оригинала е ТУ."

    prepared = prepare_model_result(result(issues=[model_issue]), source)

    assert prepared.result.issues[0].suggested_replacement is None
    assert prepared.suggested_text == source
    assert "ambiguous_issue_location" in prepared.rejected_issue_stages


def test_discards_identical_correction_hidden_in_reason() -> None:
    model_issue = issue("народна везба")
    model_issue["reason"] = "Неточност: 'народна везба' трябва да е 'народна везба'."

    prepared = prepare_model_result(result(issues=[model_issue]), SOURCE_TEXT)

    assert prepared.result.issues == ()
    assert prepared.suggested_text == SOURCE_TEXT
    assert prepared.failed_validation_rule == "no_op_issue"
    assert prepared.outcome is VisionResultOutcome.PROPOSED_TEXT_REJECTED
    assert "no_op_issue" in prepared.rejected_issue_stages


def test_keeps_valid_correction_when_another_issue_is_no_op() -> None:
    source = "Грешка!, после текст"
    valid = issue(
        "Грешка!, после текст",
        "Грешка",
        original_substring="Грешка!",
    )
    noop = issue(
        "после текст",
        "текст",
        original_substring="текст",
    )

    prepared = prepare_model_result(result(issues=[valid, noop]), source)

    assert prepared.outcome is VisionResultOutcome.ACCEPTED_PROPOSED_CORRECTION
    assert prepared.failed_validation_rule is None
    assert prepared.suggested_text == "Грешка, после текст"
    assert prepared.rejected_issue_stages == ("no_op_issue",)


def test_rejects_wrapped_prefix_duplication() -> None:
    source = "название от Раз-\nградско? засвидетелствува влияние"
    model_issue = issue(
        "градско? засвидетелствува влияние",
        "Разградско",
        original_substring="градско?",
    )

    prepared = prepare_model_result(result(issues=[model_issue]), source)

    assert prepared.suggested_text == source
    assert prepared.failed_validation_rule == "wrapped_prefix_duplication"
    assert prepared.outcome is VisionResultOutcome.PROPOSED_TEXT_REJECTED


def test_accepts_grounded_roman_range_correction_without_changing_other_numbers() -> None:
    source = "ИССФ УШ--1Х, стр. 100 и рис. УП 6."
    model_issue = issue(
        source,
        "ИССФ VIII-IX, стр. 100 и рис. УП 6.",
        original_substring=source,
    )

    prepared = prepare_model_result(result(issues=[model_issue]), source)

    assert prepared.outcome is VisionResultOutcome.ACCEPTED_PROPOSED_CORRECTION
    assert "VIII-IX" in prepared.suggested_text


def test_roman_range_exception_does_not_allow_other_number_changes() -> None:
    source = "ИССФ УШ--1Х, стр. 100."
    model_issue = issue(
        source,
        "ИССФ VIII-IX, стр. 101.",
        original_substring=source,
    )

    prepared = prepare_model_result(result(issues=[model_issue]), source)

    assert prepared.suggested_text == source
    assert prepared.failed_validation_rule == "number_grounding"


def test_deduplicates_repeated_non_actionable_passages() -> None:
    first = issue("българската народна везба съдържа")
    second = issue("народна везба")

    prepared = prepare_model_result(result(issues=[first, second]), SOURCE_TEXT)

    assert len(prepared.result.issues) == 1
    assert "duplicate_issue" in prepared.rejected_issue_stages


def test_prepares_issues_only_without_rewriting_page() -> None:
    prepared = prepare_model_result(
        result(issues=[issue("народна везба")]),
        SOURCE_TEXT,
    )

    assert prepared.outcome is VisionResultOutcome.ISSUES_ONLY
    assert prepared.suggested_text == SOURCE_TEXT
    assert len(prepared.result.issues) == 1


def test_rejected_replacement_preserves_safe_issue_guidance() -> None:
    prepared = prepare_model_result(
        result(issues=[issue(
            "българската народна везба съдържа",
            "народна везба 999",
            original_substring="народна везба",
        )]),
        SOURCE_TEXT,
    )

    assert prepared.outcome is VisionResultOutcome.PROPOSED_TEXT_REJECTED
    assert prepared.suggested_text == SOURCE_TEXT
    assert len(prepared.result.issues) == 1
    assert prepared.result.issues[0].suggested_replacement is None
    assert prepared.failed_validation_rule == "number_grounding"


def test_derives_reliable_offsets_for_unique_original_substring() -> None:
    prepared = prepare_model_result(
        result(issues=[issue(
            "българската народна везба съдържа",
            "народната везба",
            original_substring="народна везба",
        )]),
        SOURCE_TEXT,
    )

    mapped = prepared.result.issues[0]
    expected_start = SOURCE_TEXT.index("народна везба")
    assert mapped.start_offset == expected_start
    assert mapped.end_offset == expected_start + len("народна везба")
    assert SOURCE_TEXT[mapped.start_offset:mapped.end_offset] == mapped.original_substring


def test_rejects_vague_isolated_word_replacement() -> None:
    prepared = prepare_model_result(
        result(issues=[issue("везба", "шевица")]),
        SOURCE_TEXT,
    )

    assert prepared.result.issues == ()
    assert prepared.failed_validation_rule == "issue_context"


def test_rejects_non_bulgarian_issue_explanation() -> None:
    model_issue = issue("народна везба")
    model_issue["reason"] = "Possible OCR error"

    prepared = prepare_model_result(result(issues=[model_issue]), SOURCE_TEXT)

    assert prepared.result.issues == ()
    assert prepared.failed_validation_rule == "issue_language"


def test_partial_issue_rejection_preserves_usable_guidance() -> None:
    prepared = prepare_model_result(
        result(issues=[
            issue("народна везба"),
            issue("липсващ OCR откъс"),
        ]),
        SOURCE_TEXT,
    )

    assert len(prepared.result.issues) == 1
    assert prepared.result.issues[0].excerpt == "народна везба"
    assert prepared.outcome is VisionResultOutcome.PROPOSED_TEXT_REJECTED
    assert prepared.rejected_issue_stages == ("issue_excerpt",)


def test_invalid_model_offsets_are_rederived_from_authoritative_ocr() -> None:
    invalid_offsets = issue(
        "българската народна везба съдържа",
        "народната везба",
        original_substring="народна везба",
    )
    invalid_offsets.update({"startOffset": 0, "endOffset": 5})
    prepared = prepare_model_result(
        result(issues=[invalid_offsets]),
        SOURCE_TEXT,
    )

    assert len(prepared.result.issues) == 1
    assert prepared.result.requires_review is True
    mapped = prepared.result.issues[0]
    expected_start = SOURCE_TEXT.index("народна везба")
    assert mapped.start_offset == expected_start
    assert mapped.end_offset == expected_start + len("народна везба")
    assert "народната везба" in prepared.suggested_text
    assert prepared.outcome is VisionResultOutcome.ACCEPTED_PROPOSED_CORRECTION
    assert prepared.rejected_issue_stages == ()


def test_partial_model_offset_is_discarded_and_rederived() -> None:
    model_issue = issue(
        "българската народна везба съдържа",
        "народната везба",
        original_substring="народна везба",
    )
    model_issue["startOffset"] = 4

    prepared = prepare_model_result(result(issues=[model_issue]), SOURCE_TEXT)

    mapped = prepared.result.issues[0]
    assert mapped.start_offset == SOURCE_TEXT.index("народна везба")
    assert mapped.end_offset == mapped.start_offset + len("народна везба")


def test_real_size_response_keeps_highest_confidence_issues_within_json_budget() -> None:
    issues = []
    excerpts = []
    for index in range(12):
        excerpt = f"Откъс {index}: " + ("видим български текст " * 4)
        excerpts.append(excerpt)
        issues.append({
            "type": "OCR_GARBAGE",
            "excerpt": excerpt,
            "suggestedReplacement": None,
            "reason": "Вероятна грешка при разпознаването.",
            "confidence": round(0.40 + index * 0.04, 2),
        })

    model_result = result(issues=issues)
    response_bytes = len(
        model_result.model_dump_json(by_alias=True).encode("utf-8")
    )
    assert 4_500 <= response_bytes <= 5_500

    limits = SimpleNamespace(
        maximum_vision_issues=12,
        maximum_vision_issues_json_characters=4_000,
        maximum_vision_issue_code_characters=100,
        maximum_vision_excerpt_characters=500,
        maximum_vision_reason_characters=1_000,
    )
    prepared = prepare_model_result(
        model_result,
        "\n".join(excerpts),
        limits,
    )

    retained_confidences = [
        issue.confidence for issue in prepared.result.issues
    ]
    assert retained_confidences
    assert max(retained_confidences) == 0.84
    assert min(retained_confidences) > 0.40
    assert _spring_issues_json_length(prepared.result.issues) <= 4_000
    assert "issues_json_limit" in prepared.rejected_issue_stages
    assert prepared.outcome is VisionResultOutcome.PROPOSED_TEXT_REJECTED


def test_preserves_numbers_and_foreign_language_inside_contextual_replacement() -> None:
    source = "Цитатът от 1936 г. гласи Agnieszka i Tadeusz Dobrowolscv."
    prepared = prepare_model_result(
        result(issues=[issue(
            source,
            "1936 г. гласи Agnieszka i Tadeusz Dobrowolscy",
            original_substring="1936 г. гласи Agnieszka i Tadeusz Dobrowolscv",
        )]),
        source,
    )

    assert prepared.failed_validation_rule is None
    assert "1936" in prepared.suggested_text
    assert "Agnieszka i Tadeusz Dobrowolscy" in prepared.suggested_text


def test_rejects_unsupported_control_character() -> None:
    with pytest.raises(WorkerApiContractError, match="control characters"):
        validate_model_result(
            result(issues=[issue("Видим текст", "Видим\x00 текст")]),
            "Видим текст",
        )


def test_allows_newlines_tabs_and_carriage_returns() -> None:
    source = "Първи ред\nВтори\tред\rТрети ред"

    validate_model_result(
        result(issues=[issue(source, source)]),
        source,
    )


def test_rejects_ungrounded_uncertain_passage() -> None:
    passage = {
        "excerpt": "несъществуващ откъс",
        "reason": "The image is unclear",
        "confidence": 0.5,
    }

    with pytest.raises(WorkerApiContractError, match="absent from the OCR"):
        validate_model_result(
            result(uncertain_passages=[passage]),
            SOURCE_TEXT,
        )
