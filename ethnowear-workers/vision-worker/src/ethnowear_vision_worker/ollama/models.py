from dataclasses import dataclass

from pydantic import BaseModel, ConfigDict, Field

from ethnowear_worker_common.api.models import ApiModel


class VisionModelIssue(ApiModel):
    issue_type: str = Field(alias="type", min_length=1)
    excerpt: str = Field(min_length=1)
    original_substring: str | None = Field(
        default=None,
        alias="originalSubstring",
        min_length=1,
    )
    suggested_replacement: str | None = Field(default=None, min_length=1)
    start_offset: int | None = Field(default=None, alias="startOffset", ge=0)
    end_offset: int | None = Field(default=None, alias="endOffset", gt=0)
    reason: str = Field(min_length=1)
    confidence: float = Field(ge=0.0, le=1.0)


class VisionModelUncertainPassage(ApiModel):
    excerpt: str = Field(min_length=1)
    reason: str = Field(min_length=1)
    confidence: float = Field(ge=0.0, le=1.0)


class VisionModelResult(ApiModel):
    score: float = Field(ge=0.0, le=1.0)
    requires_review: bool
    suggested_text: str | None = Field(default=None, alias="suggestedText", min_length=1)
    issues: tuple[VisionModelIssue, ...] = Field(max_length=12)
    uncertain_passages: tuple[VisionModelUncertainPassage, ...] = Field(max_length=12)


@dataclass(frozen=True, slots=True)
class OllamaDiagnostics:
    done_reason: str | None
    prompt_tokens: int | None
    output_tokens: int | None
    load_duration_ns: int | None
    prompt_evaluation_duration_ns: int | None
    generation_duration_ns: int | None
    response_bytes: int


@dataclass(frozen=True, slots=True)
class VisionModelCall:
    result: VisionModelResult
    diagnostics: OllamaDiagnostics


class OllamaChatMessage(BaseModel):
    model_config = ConfigDict(extra="ignore")

    role: str
    content: str
    thinking: str | None = None


class OllamaChatResponse(BaseModel):
    model_config = ConfigDict(extra="ignore")

    model: str = Field(min_length=1)
    message: OllamaChatMessage
    done: bool
    done_reason: str | None = None
    total_duration: int | None = None
    load_duration: int | None = None
    prompt_eval_count: int | None = None
    prompt_eval_duration: int | None = None
    eval_count: int | None = None
    eval_duration: int | None = None


class OllamaModelSummary(BaseModel):
    model_config = ConfigDict(extra="ignore")

    name: str = Field(min_length=1)
    model: str = Field(min_length=1)
    digest: str = Field(pattern=r"^[0-9a-f]{64}$")


class OllamaModelsResponse(BaseModel):
    model_config = ConfigDict(extra="ignore")

    models: tuple[OllamaModelSummary, ...]
