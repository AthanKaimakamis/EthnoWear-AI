import base64
import json
from pathlib import Path

import httpx
import pytest

from ethnowear_worker_common.api.errors import WorkerApiContractError
from ethnowear_vision_worker.config import VisionWorkerSettings
from ethnowear_vision_worker.errors import VisionContractError
from ethnowear_vision_worker.ollama.client import OllamaVisionClient


def settings(
        maximum_output_bytes: int = 1_048_576,
        maximum_generated_tokens: int = 1_536,
) -> VisionWorkerSettings:
    return VisionWorkerSettings(
        api_base_url="http://api:8080",
        worker_id="vision-worker-1",
        api_token="x" * 32,
        ollama_base_url="http://ollama:11434",
        vision_model="gemma3:4b",
        prompt_version="vision-ocr-v2",
        maximum_output_bytes=maximum_output_bytes,
        maximum_generated_tokens=maximum_generated_tokens,
    )


def vision_result() -> dict[str, object]:
    return {
        "score": 0.72,
        "requiresReview": True,
        "issues": [],
        "uncertainPassages": [],
    }


def chat_response(
    *,
    model: str = "gemma3:4b",
    done: bool = True,
    content: str | None = None,
) -> dict[str, object]:
    return {
        "model": model,
        "message": {
            "role": "assistant",
            "content": content or json.dumps(vision_result(), ensure_ascii=False),
        },
        "done": done,
        "done_reason": "stop",
        "total_duration": 123,
        "load_duration": 10,
        "prompt_eval_count": 20,
        "prompt_eval_duration": 30,
        "eval_count": 40,
        "eval_duration": 50,
    }


def image_file(tmp_path: Path) -> Path:
    path = tmp_path / "page.png"
    path.write_bytes(b"\x89PNG\r\n\x1a\nimage")
    return path


MODEL_DIGEST = "a" * 64


@pytest.mark.asyncio
async def test_assess_sends_image_prompt_and_json_schema(tmp_path: Path) -> None:
    image_path = image_file(tmp_path)

    async def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/api/chat"
        body = json.loads(request.content)
        assert body["model"] == "gemma3:4b"
        assert body["stream"] is False
        assert body["think"] is False
        assert body["options"] == {
            "temperature": 0,
            "num_ctx": 8_192,
            "num_predict": 1_024,
        }
        assert body["messages"][0]["content"] == "Review this page"
        assert base64.b64decode(body["messages"][0]["images"][0]) == (
            image_path.read_bytes()
        )
        assert body["format"]["properties"]["requiresReview"]["type"] == "boolean"
        assert body["format"]["properties"]["suggestedText"]["type"] == "null"
        assert body["format"]["properties"]["issues"]["maxItems"] == 3
        assert body["format"]["properties"]["uncertainPassages"]["maxItems"] == 2
        issue = body["format"]["$defs"]["VisionModelIssue"]["properties"]
        assert issue["excerpt"]["maxLength"] == 220
        assert issue["reason"]["maxLength"] == 160
        assert issue["confidence"]["exclusiveMinimum"] == 0
        return httpx.Response(200, json=chat_response())

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        result = await OllamaVisionClient(settings(), http).assess(
            image_path,
            "Review this page",
        )

    assert result.result.score == 0.72
    assert result.result.requires_review is True
    assert result.result.issues == ()
    assert result.diagnostics.done_reason == "stop"
    assert result.diagnostics.prompt_tokens == 20
    assert result.diagnostics.output_tokens == 40
    assert result.diagnostics.load_duration_ns == 10
    assert result.diagnostics.prompt_evaluation_duration_ns == 30
    assert result.diagnostics.generation_duration_ns == 50
    assert result.diagnostics.response_bytes > 0


@pytest.mark.asyncio
async def test_rescue_mode_allows_text_and_uses_full_generation_budget(tmp_path: Path) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        body = json.loads(request.content)
        assert body["options"]["num_predict"] == 4_096
        assert body["format"]["properties"]["suggestedText"]["anyOf"]
        return httpx.Response(200, json=chat_response())

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        await OllamaVisionClient(settings(maximum_generated_tokens=4_096), http).assess(
            image_file(tmp_path),
            "Rescue this page",
            allow_rescue_transcription=True,
        )


@pytest.mark.asyncio
async def test_assess_rejects_malformed_structured_result(tmp_path: Path) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json=chat_response(content='{"score":"not-a-number"}'),
        )

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        with pytest.raises(WorkerApiContractError, match="structured vision result"):
            await OllamaVisionClient(settings(), http).assess(
                image_file(tmp_path),
                "Review this page",
            )


@pytest.mark.asyncio
async def test_assess_repairs_one_json_code_fence(tmp_path: Path) -> None:
    fenced = f"```json\n{json.dumps(vision_result())}\n```"

    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json=chat_response(content=fenced))

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        result = await OllamaVisionClient(settings(), http).assess(
            image_file(tmp_path),
            "Review this page",
        )

    assert result.result.score == 0.72


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "wrapped",
    [
        "Here is the result:\n{payload}\nDone.",
        "<think>internal reasoning</think>\n{payload}",
        "```json\n{payload}\n```\n",
    ],
)
async def test_assess_extracts_schema_valid_json_from_qwen_wrapper(
    tmp_path: Path,
    wrapped: str,
) -> None:
    content = wrapped.format(payload=json.dumps(vision_result()))

    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json=chat_response(content=content))

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        result = await OllamaVisionClient(settings(), http).assess(
            image_file(tmp_path),
            "Review this page",
        )

    assert result.result.score == 0.72


@pytest.mark.asyncio
async def test_assess_skips_non_schema_json_before_valid_result(tmp_path: Path) -> None:
    content = '{"note":"metadata"}\n' + json.dumps(vision_result())

    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json=chat_response(content=content))

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        result = await OllamaVisionClient(settings(), http).assess(
            image_file(tmp_path),
            "Review this page",
        )

    assert result.result.requires_review is True


@pytest.mark.asyncio
async def test_assess_parses_schema_valid_result_from_thinking_fallback(
    tmp_path: Path,
) -> None:
    response = chat_response(content="")
    response["message"]["thinking"] = (
        "Model reasoning followed by " + json.dumps(vision_result())
    )

    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json=response)

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        result = await OllamaVisionClient(settings(), http).assess(
            image_file(tmp_path),
            "Review this page",
        )

    assert result.result.score == 0.72


@pytest.mark.asyncio
async def test_assess_does_not_use_invalid_thinking_when_content_has_schema(
    tmp_path: Path,
) -> None:
    response = chat_response(content=json.dumps(vision_result()))
    response["message"]["thinking"] = "{invalid"

    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json=response)

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        result = await OllamaVisionClient(settings(), http).assess(
            image_file(tmp_path),
            "Review this page",
        )

    assert result.result.requires_review is True


@pytest.mark.asyncio
async def test_assess_classifies_invalid_json_separately(tmp_path: Path) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json=chat_response(content="{invalid"))

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        with pytest.raises(VisionContractError) as captured:
            await OllamaVisionClient(settings(), http).assess(
                image_file(tmp_path),
                "Review this page",
            )

    assert captured.value.stage == "response_json"


@pytest.mark.asyncio
async def test_assess_rejects_truncated_structured_result(tmp_path: Path) -> None:
    response = chat_response()
    response["done_reason"] = "length"

    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json=response)

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        with pytest.raises(WorkerApiContractError, match="truncated"):
            await OllamaVisionClient(settings(), http).assess(
                image_file(tmp_path),
                "Review this page",
            )


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("response", "message"),
    [
        (chat_response(model="other-model"), "unexpected model"),
        (chat_response(done=False), "incomplete chat response"),
        ({"invalid": True}, "invalid chat response"),
    ],
)
async def test_assess_rejects_invalid_chat_response(
    tmp_path: Path,
    response: dict[str, object],
    message: str,
) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json=response)

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        with pytest.raises(WorkerApiContractError, match=message):
            await OllamaVisionClient(settings(), http).assess(
                image_file(tmp_path),
                "Review this page",
            )


@pytest.mark.asyncio
async def test_assess_rejects_oversized_response(tmp_path: Path) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json=chat_response())

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        with pytest.raises(WorkerApiContractError, match="configured limit"):
            await OllamaVisionClient(settings(maximum_output_bytes=20), http).assess(
                image_file(tmp_path),
                "Review this page",
            )


@pytest.mark.asyncio
async def test_assess_reports_http_failure_without_raw_body(tmp_path: Path) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(503, text="private model failure")

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        with pytest.raises(RuntimeError, match="HTTP 503") as captured:
            await OllamaVisionClient(settings(), http).assess(
                image_file(tmp_path),
                "Review this page",
            )

    assert "private model failure" not in str(captured.value)


@pytest.mark.asyncio
async def test_assess_rejects_empty_prompt_before_request(tmp_path: Path) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        raise AssertionError("Ollama must not be called")

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        with pytest.raises(ValueError, match="must not be empty"):
            await OllamaVisionClient(settings(), http).assess(
                image_file(tmp_path),
                "   ",
            )


def test_encode_image_rejects_missing_or_empty_file(tmp_path: Path) -> None:
    with pytest.raises(ValueError, match="does not exist"):
        OllamaVisionClient._encode_image(tmp_path / "missing.png")

    empty = tmp_path / "empty.png"
    empty.touch()

    with pytest.raises(ValueError, match="is empty"):
        OllamaVisionClient._encode_image(empty)


@pytest.mark.asyncio
async def test_resolve_model_version_returns_and_caches_digest() -> None:
    request_count = 0

    async def handler(request: httpx.Request) -> httpx.Response:
        nonlocal request_count
        request_count += 1
        assert request.method == "GET"
        assert request.url.path == "/api/tags"
        return httpx.Response(
            200,
            json={
                "models": [
                    {
                        "name": "gemma3:4b",
                        "model": "gemma3:4b",
                        "digest": MODEL_DIGEST,
                        "size": 3_000_000_000,
                    }
                ]
            },
        )

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        client = OllamaVisionClient(settings(), http)
        first = await client.resolve_model_version()
        second = await client.resolve_model_version()

    assert first == MODEL_DIGEST
    assert second == MODEL_DIGEST
    assert request_count == 1


@pytest.mark.asyncio
async def test_resolve_model_version_rejects_unavailable_model() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "models": [
                    {
                        "name": "other-model:latest",
                        "model": "other-model:latest",
                        "digest": MODEL_DIGEST,
                    }
                ]
            },
        )

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        with pytest.raises(RuntimeError, match="configured.*unavailable"):
            await OllamaVisionClient(settings(), http).resolve_model_version()


@pytest.mark.asyncio
async def test_resolve_model_version_rejects_malformed_metadata() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "models": [
                    {
                        "name": "gemma3:4b",
                        "model": "gemma3:4b",
                        "digest": "invalid",
                    }
                ]
            },
        )

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        with pytest.raises(WorkerApiContractError, match="model-list response"):
            await OllamaVisionClient(settings(), http).resolve_model_version()


@pytest.mark.asyncio
async def test_resolve_model_version_reports_http_status_only() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(503, text="private Ollama error")

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        with pytest.raises(RuntimeError, match="HTTP 503") as captured:
            await OllamaVisionClient(settings(), http).resolve_model_version()

    assert "private Ollama error" not in str(captured.value)
