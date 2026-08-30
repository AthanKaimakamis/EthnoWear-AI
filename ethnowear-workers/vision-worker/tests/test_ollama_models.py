import pytest
from pydantic import ValidationError

from ethnowear_vision_worker.ollama.models import VisionModelResult


def model_result() -> dict[str, object]:
    return {
        "score": 0.72,
        "requiresReview": True,
        "issues": [
            {
                "type": "OCR_GARBAGE",
                "excerpt": "719 1604",
                "suggestedReplacement": None,
                "reason": "The numbers are not visible as page text",
                "confidence": 0.95,
            }
        ],
        "uncertainPassages": [
            {
                "excerpt": "неясен откъс",
                "reason": "The scan is blurred",
                "confidence": 0.65,
            }
        ],
    }


def test_parses_strict_vision_model_result() -> None:
    result = VisionModelResult.model_validate(model_result())

    assert result.requires_review is True
    assert result.issues[0].issue_type == "OCR_GARBAGE"
    assert result.uncertain_passages[0].confidence == 0.65


def test_parses_actionable_issue_context_and_offsets() -> None:
    body = model_result()
    body["issues"][0].update({
        "excerpt": "Контекст преди 719 1604 след него",
        "originalSubstring": "719 1604",
        "startOffset": 15,
        "endOffset": 23,
        "reason": "Числата не се виждат в изображението",
    })

    issue = VisionModelResult.model_validate(body).issues[0]

    assert issue.original_substring == "719 1604"
    assert issue.start_offset == 15
    assert issue.end_offset == 23


def test_rejects_unknown_model_output_fields() -> None:
    body = model_result()
    body["approval"] = "APPROVED"

    with pytest.raises(ValidationError, match="Extra inputs are not permitted"):
        VisionModelResult.model_validate(body)


@pytest.mark.parametrize("field", ["score", "requiresReview", "issues", "uncertainPassages"])
def test_rejects_missing_required_model_output(field: str) -> None:
    body = model_result()
    del body[field]

    with pytest.raises(ValidationError, match="Field required"):
        VisionModelResult.model_validate(body)


def test_rejects_out_of_range_model_confidence() -> None:
    body = model_result()
    body["issues"][0]["confidence"] = 1.1

    with pytest.raises(ValidationError, match="confidence"):
        VisionModelResult.model_validate(body)


def test_rejects_blank_suggested_replacement() -> None:
    body = model_result()
    body["issues"][0]["suggestedReplacement"] = ""

    with pytest.raises(ValidationError, match="suggestedReplacement"):
        VisionModelResult.model_validate(body)
