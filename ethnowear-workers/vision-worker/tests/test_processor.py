import logging
from pathlib import Path
from types import SimpleNamespace

import pytest
from pydantic import SecretStr

from ethnowear_worker_common.api.errors import WorkerApiContractError
from ethnowear_worker_common.jobs.context import ClaimCredentials
from ethnowear_vision_worker.api.models import WorkerJobType
from ethnowear_vision_worker.errors import VisionContractError
from ethnowear_vision_worker.jobs import processor
from ethnowear_vision_worker.jobs.processor import (
    VisionAssessmentOperation,
    VisionAssessmentProcessor,
)
from ethnowear_vision_worker.assessment.validation import (
    PreparedVisionResult,
    VisionResultOutcome,
)
from ethnowear_vision_worker.ollama.models import (
    OllamaDiagnostics,
    VisionModelCall,
    VisionModelResult,
)


@pytest.fixture(autouse=True)
def stub_image_optimization(monkeypatch: pytest.MonkeyPatch) -> None:
    def optimize(source: Path, destination: Path, **_kwargs: object) -> object:
        destination.write_bytes(source.read_bytes())
        return SimpleNamespace(
            width=1_000,
            height=2_000,
            size_bytes=destination.stat().st_size,
            resized=False,
        )

    monkeypatch.setattr(processor, "optimize_for_vision", optimize)


def claim() -> SimpleNamespace:
    return SimpleNamespace(
        job_id=20,
        job_type=WorkerJobType.VISION_OCR_ASSESSMENT,
        attempt=2,
        target=SimpleNamespace(
            document_id=10,
            document_page_id=98,
            input_available=True,
        ),
        limits=SimpleNamespace(
            maximum_input_bytes=1_000,
            maximum_ocr_text_characters=5_000,
            maximum_pixel_width=2_000,
            maximum_pixel_height=3_000,
            maximum_page_pixels=6_000_000,
        ),
    )


def context() -> SimpleNamespace:
    return SimpleNamespace(
        job_id=20,
        document_id=10,
        document_page_id=98,
        ocr_result_id=51,
        input_media_id=31,
        image_size_bytes=12,
        image_width=1_000,
        image_height=2_000,
        raw_ocr_text="Разпознат български текст",
    )


def response() -> SimpleNamespace:
    return SimpleNamespace(
        job_id=20,
        document_id=10,
        page_id=98,
        ocr_result_id=51,
        input_media_id=31,
    )


class FakeApiClient:
    def __init__(self, assessment_context: SimpleNamespace) -> None:
        self.context = assessment_context
        self.received_credentials: list[ClaimCredentials] = []
        self.submitted_request: object | None = None

    async def get_assessment_context(
        self,
        credentials: ClaimCredentials,
    ) -> SimpleNamespace:
        self.received_credentials.append(credentials)
        return self.context

    async def download_input(
        self,
        credentials: ClaimCredentials,
        destination: Path,
        maximum_bytes: int,
    ) -> int:
        self.received_credentials.append(credentials)
        assert maximum_bytes == 1_000
        destination.write_bytes(b"image-content")
        return len(b"image-content")

    async def submit_assessment(
        self,
        credentials: ClaimCredentials,
        request: object,
    ) -> SimpleNamespace:
        self.received_credentials.append(credentials)
        self.submitted_request = request
        return response()


class FakeOllamaClient:
    def __init__(self) -> None:
        self.assessed_path: Path | None = None
        self.assessed_prompt: str | None = None

    async def resolve_model_version(self) -> str:
        return "a" * 64

    async def assess(self, image_path: Path, prompt: str, **_kwargs: object) -> object:
        assert image_path.read_bytes() == b"image-content"
        self.assessed_path = image_path
        self.assessed_prompt = prompt
        return object()


DIAGNOSTICS = OllamaDiagnostics(
    done_reason="stop",
    prompt_tokens=100,
    output_tokens=20,
    load_duration_ns=1,
    prompt_evaluation_duration_ns=2,
    generation_duration_ns=3,
    response_bytes=400,
)


@pytest.mark.asyncio
async def test_operation_runs_complete_pipeline_and_cleans_workspace(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    assessment_context = context()
    assessment_context.image_size_bytes = len(b"image-content")
    api_client = FakeApiClient(assessment_context)
    ollama_client = FakeOllamaClient()
    settings = SimpleNamespace(
        temporary_root=tmp_path,
        prompt_version="vision-ocr-v2",
        vision_model="gemma3:4b",
        maximum_image_edge_pixels=2_048,
        image_jpeg_quality=90,
    )
    credentials = ClaimCredentials(20, SecretStr("claim-token"))
    model_result = object()
    model_call = SimpleNamespace(result=model_result, diagnostics=DIAGNOSTICS)
    assessment_request = object()
    validated: list[tuple[object, str]] = []

    monkeypatch.setattr(
        processor,
        "build_vision_prompt",
        lambda supplied_context, version: "bounded prompt",
    )
    monkeypatch.setattr(
        ollama_client,
        "assess",
        _returning_assessment(ollama_client, model_call),
    )
    monkeypatch.setattr(
        processor,
        "prepare_model_result",
        lambda result, text, _limits, **_kwargs: (
            validated.append((result, text))
            or SimpleNamespace(
                result=result,
                suggested_text=text,
                outcome=VisionResultOutcome.NO_DISCREPANCY,
                failed_validation_rule=None,
                rejected_issue_stages=(),
            )
        ),
    )
    monkeypatch.setattr(
        processor,
        "build_assessment_request",
        lambda result, version, supplied_settings, limits, **kwargs: assessment_request,
    )

    operation = VisionAssessmentOperation(
        api_client=api_client,
        ollama_client=ollama_client,
        settings=settings,
    )
    await operation.run(claim(), credentials)

    assert api_client.received_credentials == [
        credentials,
        credentials,
        credentials,
    ]
    assert ollama_client.assessed_prompt == "bounded prompt"
    assert validated == [(model_result, assessment_context.raw_ocr_text)]
    assert api_client.submitted_request is assessment_request
    assert list(tmp_path.iterdir()) == []


@pytest.mark.asyncio
async def test_grounding_failure_submits_unchanged_ocr_for_review(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
    caplog: pytest.LogCaptureFixture,
) -> None:
    assessment_context = context()
    assessment_context.image_size_bytes = len(b"image-content")
    api_client = FakeApiClient(assessment_context)
    ollama_client = FakeOllamaClient()
    settings = SimpleNamespace(
        temporary_root=tmp_path,
        prompt_version="vision-ocr-v2",
        vision_model="gemma3:4b",
        maximum_image_edge_pixels=2_048,
        image_jpeg_quality=90,
    )
    model_result = VisionModelResult(
        score=0.9,
        requires_review=False,
        issues=(),
        uncertain_passages=(),
    )
    captured: dict[str, object] = {}

    monkeypatch.setattr(processor, "build_vision_prompt", lambda *_: "prompt")
    monkeypatch.setattr(
        ollama_client,
        "assess",
        _returning_assessment(
            ollama_client,
            VisionModelCall(model_result, DIAGNOSTICS),
        ),
    )

    def reject_suggestion(*_args: object, **_kwargs: object) -> PreparedVisionResult:
        return PreparedVisionResult(
            result=model_result.model_copy(update={"requires_review": True}),
            suggested_text=assessment_context.raw_ocr_text,
            outcome=VisionResultOutcome.PROPOSED_TEXT_REJECTED,
            failed_validation_rule="number_grounding",
        )

    def capture_request(
        result: VisionModelResult,
        *_args: object,
        **kwargs: object,
    ) -> object:
        captured["result"] = result
        captured["stage"] = kwargs["rejected_suggestion_stage"]
        captured["suggested_text"] = kwargs["suggested_text"]
        return object()

    monkeypatch.setattr(processor, "prepare_model_result", reject_suggestion)
    monkeypatch.setattr(processor, "build_assessment_request", capture_request)

    with caplog.at_level(logging.WARNING):
        await VisionAssessmentOperation(
            api_client=api_client,
            ollama_client=ollama_client,
            settings=settings,
        ).run(claim(), ClaimCredentials(20, SecretStr("claim-token")))

    fallback = captured["result"]
    assert isinstance(fallback, VisionModelResult)
    assert fallback.requires_review is True
    assert fallback.score == 0.9
    assert captured["stage"] == "number_grounding"
    assert captured["suggested_text"] == assessment_context.raw_ocr_text
    assert "private details" not in caplog.text


@pytest.mark.asyncio
async def test_truncated_response_retries_once_with_compact_prompt(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    assessment_context = context()
    assessment_context.image_size_bytes = len(b"image-content")
    api_client = FakeApiClient(assessment_context)
    ollama_client = FakeOllamaClient()
    settings = SimpleNamespace(
        temporary_root=tmp_path,
        prompt_version="vision-ocr-v2",
        vision_model="gemma3:4b",
        maximum_image_edge_pixels=2_048,
        image_jpeg_quality=90,
    )
    model_result = VisionModelResult(
        score=0.7,
        requires_review=True,
        issues=(),
        uncertain_passages=(),
    )
    calls: list[str] = []
    captured: dict[str, object] = {}

    async def assess(_image_path: Path, prompt: str, **_kwargs: object) -> VisionModelCall:
        calls.append(prompt)
        if len(calls) == 1:
            raise VisionContractError(
                "response_truncated",
                "private truncated output",
                diagnostics=DIAGNOSTICS,
            )
        return VisionModelCall(model_result, DIAGNOSTICS)

    monkeypatch.setattr(processor, "build_vision_prompt", lambda *_: "full")
    monkeypatch.setattr(processor, "build_compact_retry_prompt", lambda *_: "compact")
    monkeypatch.setattr(ollama_client, "assess", assess)
    monkeypatch.setattr(
        processor,
        "build_assessment_request",
        lambda *_args, **kwargs: captured.update(kwargs) or object(),
    )

    await VisionAssessmentOperation(
        api_client=api_client,
        ollama_client=ollama_client,
        settings=settings,
    ).run(claim(), ClaimCredentials(20, SecretStr("claim-token")))

    assert calls == ["full", "compact"]
    assert captured["compact_retry_used"] is True
    assert captured["diagnostics"] == DIAGNOSTICS


def _returning_assessment(
    client: FakeOllamaClient,
    result: object,
):
    async def assess(image_path: Path, prompt: str, **_kwargs: object) -> object:
        assert image_path.read_bytes() == b"image-content"
        client.assessed_path = image_path
        client.assessed_prompt = prompt
        return result

    return assess


@pytest.mark.parametrize(
    ("field", "value", "message"),
    [
        ("job_id", 21, "different job"),
        ("document_id", 11, "different document"),
        ("document_page_id", 99, "different page"),
        ("image_size_bytes", 1_001, "size limit"),
        ("raw_ocr_text", "x" * 5_001, "OCR text"),
        ("image_width", 2_001, "maximum width"),
        ("image_height", 3_001, "maximum height"),
    ],
)
def test_context_validation_rejects_invalid_or_oversized_evidence(
    field: str,
    value: object,
    message: str,
) -> None:
    supplied_context = context()
    setattr(supplied_context, field, value)

    with pytest.raises(WorkerApiContractError, match=message):
        VisionAssessmentOperation._validate_context(claim(), supplied_context)


def test_context_validation_rejects_excessive_pixel_count() -> None:
    supplied_context = context()
    supplied_context.image_width = 2_000
    supplied_context.image_height = 3_001
    claimed_job = claim()
    claimed_job.limits.maximum_pixel_height = 4_000

    with pytest.raises(WorkerApiContractError, match="pixel count"):
        VisionAssessmentOperation._validate_context(claimed_job, supplied_context)


@pytest.mark.parametrize(
    ("field", "value", "message"),
    [
        ("job_id", 21, "different job"),
        ("document_id", 11, "different document"),
        ("page_id", 99, "different page"),
        ("ocr_result_id", 52, "different OCR result"),
        ("input_media_id", 32, "different input media"),
    ],
)
def test_response_validation_rejects_stale_identity(
    field: str,
    value: object,
    message: str,
) -> None:
    accepted = response()
    setattr(accepted, field, value)

    with pytest.raises(WorkerApiContractError, match=message):
        VisionAssessmentOperation._validate_response(
            claim(),
            context(),
            accepted,
        )


@pytest.mark.asyncio
async def test_processor_delegates_to_shared_execution(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    captured: dict[str, object] = {}
    operation_calls: list[tuple[object, ClaimCredentials]] = []
    credentials = ClaimCredentials(20, SecretStr("claim-token"))

    class Operation:
        async def run(
            self,
            supplied_claim: object,
            supplied_credentials: ClaimCredentials,
        ) -> None:
            operation_calls.append((supplied_claim, supplied_credentials))

    async def fake_execute_claimed_job(**arguments: object) -> None:
        captured.update(arguments)
        await arguments["operation"](credentials)

    monkeypatch.setattr(
        processor,
        "execute_claimed_job",
        fake_execute_claimed_job,
    )

    api_client = object()
    operation = Operation()
    claimed_job = claim()
    vision_processor = VisionAssessmentProcessor(
        api_client=api_client,
        operation=operation,
    )

    await vision_processor.process(claimed_job)

    assert captured["api_client"] is api_client
    assert captured["claim"] is claimed_job
    assert operation_calls == [(claimed_job, credentials)]
