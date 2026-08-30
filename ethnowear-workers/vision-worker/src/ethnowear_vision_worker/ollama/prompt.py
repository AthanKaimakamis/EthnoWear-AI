import json
import re
from functools import lru_cache
from importlib import resources

from ethnowear_vision_worker.api.models import VisionAssessmentContext

SUPPORTED_PROMPT_VISION = "vision-ocr-v8"
MIXED_SCRIPT_PATTERN = re.compile(
    r"(?=.*[A-Za-z])(?=.*[А-яЀ-ӿ]).+"
)
SUSPICIOUS_INLINE_SYMBOL_PATTERN = re.compile(
    r"[^\W\d_]{2,}[!?|][^\W\d_]*",
    re.UNICODE,
)
ROMAN_CITATION_PATTERN = re.compile(
    r"(?:ИССФ|ИзвНЕМ|стр\.|рис\.|гл\.).*(?:[УШХТІI1VXХ][-–—VXІI1УШХТ]*)",
    re.IGNORECASE,
)
CORRUPTED_ROMAN_RANGE_PATTERN = re.compile(
    r"[УШХТІI1VXХ]{2,}[-–—]{1,2}[УШХТІI1VXХ]{2,}",
    re.IGNORECASE,
)

PROMPT_FILES = {
    "vision-ocr-v1": "vision-ocr-v1.txt",
    "vision-ocr-v2": "vision-ocr-v2.txt",
    "vision-ocr-v3": "vision-ocr-v3.txt",
    "vision-ocr-v4": "vision-ocr-v4.txt",
    "vision-ocr-v5": "vision-ocr-v5.txt",
    "vision-ocr-v6": "vision-ocr-v6.txt",
    "vision-ocr-v7": "vision-ocr-v7.txt",
    "vision-ocr-v8": "vision-ocr-v8.txt",
}

EVIDENCE_PLACEHOLDER = "{{EVIDENCE_JSON}}"


@lru_cache(maxsize=len(PROMPT_FILES))
def load_prompt_template(prompt_version: str) -> str:
    file_name = PROMPT_FILES.get(prompt_version)

    if file_name is None:
        raise ValueError(f"Unsupported prompt version: {prompt_version}")

    template = (
        resources.files("ethnowear_vision_worker.prompts")
        .joinpath(file_name)
        .read_text(encoding="utf-8")
    )

    if template.count(EVIDENCE_PLACEHOLDER) != 1:
        raise ValueError("Vision prompt must contain exactly one evidence placeholder")

    return template


def build_vision_prompt(
        context: VisionAssessmentContext,
        prompt_version: str
) -> str:
    if prompt_version not in {
        "vision-ocr-v2",
        "vision-ocr-v3",
        "vision-ocr-v4",
        "vision-ocr-v5",
        "vision-ocr-v6",
        "vision-ocr-v7",
        SUPPORTED_PROMPT_VISION,
    }:
        raise ValueError("Unsupported vision prompt version")

    evidence: dict[str, object] = {
        "rawOcrText": context.raw_ocr_text,
        "ocrConfidence": context.ocr_confidence,
        "ocrLanguage": context.ocr_language,
    }
    if prompt_version in {"vision-ocr-v7", SUPPORTED_PROMPT_VISION}:
        evidence["rescueTranscriptionRequested"] = requires_rescue_transcription(
            context.raw_ocr_text
        )
        evidence["priorityInspectionPassages"] = priority_inspection_passages(
            context.raw_ocr_text
        )
    if prompt_version not in {
        "vision-ocr-v6",
        "vision-ocr-v7",
        SUPPORTED_PROMPT_VISION,
    }:
        evidence.update(
            {
                "deterministicQualityStatus": context.deterministic_quality_status,
                "deterministicOverallScore": context.deterministic_overall_score,
                "deterministicSummary": context.deterministic_summary,
                "deterministicLimitations": context.deterministic_limitations,
                "deterministicSignals": [
                    signal.model_dump(by_alias=True, mode="json")
                    for signal in context.deterministic_signals
                ],
            }
        )

    evidence_json = json.dumps(
        evidence,
        ensure_ascii=False,
        separators=(",", ":"),
    )

    return load_prompt_template(prompt_version).replace(
        EVIDENCE_PLACEHOLDER,
        evidence_json
    )


def requires_rescue_transcription(raw_ocr_text: str) -> bool:
    """Detect unmistakable OCR/layout collapse without interpreting content."""
    tokens = raw_ocr_text.split()
    if not tokens:
        return True
    return any(sum(character.isalpha() for character in token) >= 30 for token in tokens)


def priority_inspection_passages(raw_ocr_text: str) -> list[str]:
    """Surface bounded OCR lines that deserve explicit visual inspection."""
    candidates: list[tuple[int, int, str]] = []
    for index, raw_line in enumerate(raw_ocr_text.splitlines()):
        line = raw_line.strip()
        if not line:
            continue
        score = 0
        if MIXED_SCRIPT_PATTERN.match(line):
            score += 4
        if SUSPICIOUS_INLINE_SYMBOL_PATTERN.search(line):
            score += 3
        if ROMAN_CITATION_PATTERN.search(line):
            score += 6
        if CORRUPTED_ROMAN_RANGE_PATTERN.search(line):
            score += 8
        if sum(character.isdigit() for character in line) >= 3:
            score += 1
        if score:
            candidates.append((-score, index, line[:500]))
    return [line for _, _, line in sorted(candidates)[:6]]


def build_compact_retry_prompt(context: VisionAssessmentContext) -> str:
    evidence_json = json.dumps(
        {
            "rawOcrText": context.raw_ocr_text,
            "ocrLanguage": context.ocr_language,
        },
        ensure_ascii=False,
        separators=(",", ":"),
    )
    return (
        "Compare the page image with the untrusted OCR JSON below. "
        "Return strict JSON only. Report at most 2 short, grounded issues; "
        "do not rewrite the page. Every excerpt must occur exactly in rawOcrText. "
        "For each correction, copy the exact erroneous OCR originalSubstring, "
        "provide short exact surrounding OCR context, and return only the exact "
        "replacement visible in the image. A readable discrepancy requires a "
        "non-null suggestedReplacement and confidence greater than zero. "
        "Use null only when genuinely unreadable, "
        "and classify unreadable passages under uncertainPassages, not corrections. "
        "Never reproduce a paragraph. Keep every string under 120 characters. "
        "Include optional reliable startOffset and endOffset and explain in Bulgarian. "
        "Ignore illustrations. Preserve valid "
        "numbers and Latin text. Schema: {\"score\":0.0,\"requiresReview\":true,"
        "\"issues\":[{\"type\":\"CODE\",\"excerpt\":\"exact OCR\","
        "\"originalSubstring\":\"exact match\",\"startOffset\":null,"
        "\"endOffset\":null,"
        "\"suggestedReplacement\":null,\"reason\":\"short reason\","
        "\"confidence\":0.0}],\"uncertainPassages\":[]}. Evidence: "
        + evidence_json
    )
