import base64
import json
import logging
from pathlib import Path

import httpx
from pydantic import ValidationError

from ethnowear_vision_worker.config import VisionWorkerSettings
from ethnowear_vision_worker.errors import VisionContractError
from ethnowear_vision_worker.ollama.models import (
    OllamaChatResponse,
    OllamaDiagnostics,
    OllamaModelsResponse,
    VisionModelCall,
    VisionModelResult
)


logger = logging.getLogger(__name__)


class OllamaVisionClient:
    CHAT_PATH = "/api/chat"
    TAGS_PATH = "/api/tags"

    def __init__(
            self,
            settings: VisionWorkerSettings,
            http_client: httpx.AsyncClient,
    ) -> None:
        self._base_url = settings.ollama_base_url.rstrip("/")
        self._model = settings.vision_model
        self._model_version: str | None = None
        self._timeout_seconds = settings.vision_timeout_seconds
        self._maximum_output_bytes = settings.maximum_output_bytes
        self._context_tokens = settings.context_tokens
        self._maximum_generated_tokens = settings.maximum_generated_tokens
        self._http_client = http_client

    async def assess(
            self,
            image_path: Path,
            prompt: str,
            *,
            allow_rescue_transcription: bool = False,
            maximum_issues: int = 3,
            maximum_tokens: int | None = None,
    ) -> VisionModelCall:
        if not prompt.strip():
            raise ValueError("Vision prompt must not be empty")

        image_base64 = self._encode_image(image_path)

        request = {
            "model": self._model,
            "messages": [
                {
                    "role": "user",
                    "content": prompt,
                    "images": [image_base64],
                }
            ],
            "stream": False,
            "think": False,
            "format": self._response_schema(
                allow_rescue_transcription,
                maximum_issues,
            ),
            "options": {
                "temperature": 0,
                "num_ctx": self._context_tokens,
                "num_predict": (
                    min(self._maximum_generated_tokens, maximum_tokens)
                    if maximum_tokens is not None
                    else self._maximum_generated_tokens
                    if allow_rescue_transcription
                    else min(self._maximum_generated_tokens, 1_024)
                ),
            },
        }

        response_body = await self._send(request)

        try:
            response = OllamaChatResponse.model_validate_json(response_body)
        except ValidationError as error:
            raise VisionContractError(
                "response_schema",
                "Ollama returned an invalid chat response",
            ) from error

        diagnostics = OllamaDiagnostics(
            done_reason=response.done_reason,
            prompt_tokens=response.prompt_eval_count,
            output_tokens=response.eval_count,
            load_duration_ns=response.load_duration,
            prompt_evaluation_duration_ns=response.prompt_eval_duration,
            generation_duration_ns=response.eval_duration,
            response_bytes=len(response_body),
        )

        self._log_diagnostics(image_path, prompt, response, diagnostics)

        if not response.done:
            raise VisionContractError(
                "response_schema",
                "Ollama returned an incomplete chat response",
                diagnostics=diagnostics,
            )

        if response.done_reason == "length":
            raise VisionContractError(
                "response_truncated",
                "Ollama truncated the structured vision response",
                diagnostics=diagnostics,
            )

        if response.model != self._model:
            raise VisionContractError(
                "model_mismatch",
                "Ollama returned a response from an unexpected model",
            )

        return VisionModelCall(
            result=self._parse_model_result(
                response.message.content,
                response.message.thinking,
            ),
            diagnostics=diagnostics,
        )

    @staticmethod
    def _response_schema(
            allow_rescue_transcription: bool,
            maximum_issues: int = 3,
    ) -> dict[str, object]:
        if maximum_issues <= 0:
            raise ValueError("Maximum vision issues must be positive")
        schema = VisionModelResult.model_json_schema(by_alias=True)
        schema["properties"]["issues"]["maxItems"] = maximum_issues
        schema["properties"]["uncertainPassages"]["maxItems"] = 2

        issue_properties = schema["$defs"]["VisionModelIssue"]["properties"]
        issue_properties["type"]["maxLength"] = 40
        issue_properties["excerpt"]["maxLength"] = 220
        issue_properties["originalSubstring"]["anyOf"][0]["maxLength"] = 120
        issue_properties["suggestedReplacement"]["anyOf"][0]["maxLength"] = 120
        issue_properties["reason"]["maxLength"] = 160
        issue_properties["confidence"].pop("minimum", None)
        issue_properties["confidence"]["exclusiveMinimum"] = 0.0

        uncertain_properties = schema["$defs"][
            "VisionModelUncertainPassage"
        ]["properties"]
        uncertain_properties["excerpt"]["maxLength"] = 220
        uncertain_properties["reason"]["maxLength"] = 160
        if not allow_rescue_transcription:
            schema["properties"]["suggestedText"] = {
                "type": "null",
                "default": None,
                "description": "Full-page transcription is disabled for this request",
            }
        return schema

    def _log_diagnostics(
            self,
            image_path: Path,
            prompt: str,
            response: OllamaChatResponse,
            diagnostics: OllamaDiagnostics,
    ) -> None:
        logger.info(
            "vision_model_call_completed",
            extra={
                "model": self._model,
                "prompt_characters": len(prompt),
                "image_bytes": image_path.stat().st_size,
                "response_bytes": diagnostics.response_bytes,
                "done_reason": diagnostics.done_reason,
                "total_duration_ns": response.total_duration,
                "load_duration_ns": response.load_duration,
                "prompt_eval_count": response.prompt_eval_count,
                "prompt_eval_duration_ns": response.prompt_eval_duration,
                "eval_count": response.eval_count,
                "eval_duration_ns": response.eval_duration,
            },
        )

    @staticmethod
    def _parse_model_result(
            content: str,
            thinking: str | None = None,
    ) -> VisionModelResult:
        candidates = _json_object_candidates(content)
        used_thinking_fallback = False
        if not candidates and thinking:
            candidates = _json_object_candidates(thinking)
            used_thinking_fallback = bool(candidates)
        if not candidates:
            raise VisionContractError(
                "response_json",
                "Ollama returned invalid vision JSON",
            )

        last_validation_error: ValidationError | None = None
        for value, repaired in candidates:
            try:
                result = VisionModelResult.model_validate(value)
            except ValidationError as error:
                last_validation_error = error
                continue

            if repaired or used_thinking_fallback:
                logger.info(
                    "vision_response_json_format_repaired",
                    extra={"thinking_fallback_used": used_thinking_fallback},
                )
            return result

        if last_validation_error is not None:
            raise VisionContractError(
                "response_schema",
                "Ollama returned an invalid structured vision result",
            ) from last_validation_error

        raise VisionContractError(
            "response_json",
            "Ollama returned invalid vision JSON",
        )

    async def _send(self, request: dict[str, object]) -> bytes:
        timeout = httpx.Timeout(self._timeout_seconds)

        async with self._http_client.stream(
                "POST",
                f"{self._base_url}{self.CHAT_PATH}",
                json=request,
                timeout=timeout,
        ) as response:
            if response.status_code != 200:
                await response.aread()
                raise RuntimeError(f"Ollama vision request failed with HTTP {response.status_code}")

            output = bytearray()

            async for chunk in response.aiter_bytes():
                output.extend(chunk)

                if len(output) > self._maximum_output_bytes:
                    raise VisionContractError(
                        "response_size",
                        "Ollama vision response exceeds the configured limit",
                    )

        if not output:
            raise VisionContractError(
                "response_schema",
                "Ollama returned an empty vision response",
            )

        return bytes(output)

    async def resolve_model_version(self) -> str:
        if self._model_version is not None:
            return self._model_version

        response = await self._http_client.get(
            f"{self._base_url}{self.TAGS_PATH}",
            timeout=httpx.Timeout(self._timeout_seconds),
        )

        if response.status_code != 200:
            raise RuntimeError(f"Ollama model-list request failed with HTTP {response.status_code}")

        try:
            available = OllamaModelsResponse.model_validate_json(response.content)
        except ValidationError as error:
            raise VisionContractError(
                "model_metadata",
                "Ollama returned an invalid model-list response",
            ) from error

        for model in available.models:
            if self._model in {model.name, model.model}:
                self._model_version = model.digest
                return model.digest

        raise RuntimeError("The configured Ollama vision model is unavailable")

    @staticmethod
    def _encode_image(image_path: Path) -> str:
        if not image_path.is_file():
            raise ValueError("Vision input image does not exist")

        image = image_path.read_bytes()

        if not image:
            raise ValueError("Vision input image is empty")

        return base64.b64encode(image).decode("ascii")


def _unwrap_json_fence(content: str) -> str:
    stripped = content.strip()

    if not stripped.startswith("```") or not stripped.endswith("```"):
        return stripped

    first_line, separator, remainder = stripped.partition("\n")

    if not separator or first_line not in {"```", "```json"}:
        return stripped

    return remainder[:-3].strip()


def _json_object_candidates(content: str) -> list[tuple[object, bool]]:
    """Return bounded JSON candidates, including JSON surrounded by model prose."""
    stripped = content.strip()
    if not stripped:
        return []

    candidates: list[tuple[object, bool]] = []
    seen: set[str] = set()

    def add(value: object, repaired: bool) -> None:
        if not isinstance(value, dict):
            return
        key = json.dumps(value, sort_keys=True, ensure_ascii=False)
        if key not in seen:
            seen.add(key)
            candidates.append((value, repaired))

    for candidate, repaired in (
        (stripped, False),
        (_unwrap_json_fence(stripped), True),
    ):
        try:
            add(json.loads(candidate), repaired)
        except json.JSONDecodeError:
            pass

    decoder = json.JSONDecoder()
    for index, character in enumerate(stripped):
        if character != "{":
            continue
        try:
            value, _end = decoder.raw_decode(stripped, index)
        except json.JSONDecodeError:
            continue
        add(value, True)

    return candidates
