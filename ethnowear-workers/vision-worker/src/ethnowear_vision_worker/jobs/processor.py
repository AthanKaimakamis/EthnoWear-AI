import logging

from ethnowear_worker_common.api.errors import WorkerApiContractError
from ethnowear_worker_common.jobs.context import ClaimCredentials
from ethnowear_worker_common.temp import JobWorkspace

from ethnowear_vision_worker.api.client import VisionWorkerApiClient
from ethnowear_vision_worker.api.models import (
    ClaimResponse,
    VisionAssessmentContext,
    VisionAssessmentResponse,
    WorkerJobType,
)
from ethnowear_vision_worker.assessment.mapper import build_assessment_request
from ethnowear_vision_worker.assessment.validation import prepare_model_result
from ethnowear_vision_worker.config import VisionWorkerSettings
from ethnowear_vision_worker.errors import VisionContractError
from ethnowear_vision_worker.jobs.execution import execute_claimed_job
from ethnowear_vision_worker.ollama.client import OllamaVisionClient
from ethnowear_vision_worker.ollama.prompt import (
    build_compact_retry_prompt,
    build_vision_prompt,
    requires_rescue_transcription,
)
from ethnowear_vision_worker.image import optimize_for_vision


logger = logging.getLogger(__name__)


class VisionAssessmentOperation:
    def __init__(
            self,
            *,
            api_client: VisionWorkerApiClient,
            ollama_client: OllamaVisionClient,
            settings: VisionWorkerSettings
    ) -> None:
        self._api_client = api_client
        self._ollama_client = ollama_client
        self._settings = settings

    async def run(self, claim: ClaimResponse, credentials: ClaimCredentials) -> None:
        processing_stage = "claim_validation"
        try:
            self._validate_claim(claim)
            processing_stage = await self._run_pipeline(claim, credentials)
        except Exception as error:
            logger.error(
                "vision_processing_stage_failed",
                extra={
                    "job_id": claim.job_id,
                    "processing_stage": getattr(
                        error,
                        "processing_stage",
                        processing_stage,
                    ),
                    "validation_stage": getattr(error, "stage", None),
                    "exception_type": type(error).__name__,
                },
            )
            raise

    async def _run_pipeline(
            self,
            claim: ClaimResponse,
            credentials: ClaimCredentials,
    ) -> str:
        processing_stage = "workspace"
        try:
            with JobWorkspace.create(
                self._settings.temporary_root,
                claim.job_id,
                claim.attempt
            ) as workspace:
                processing_stage = "context_fetch"
                context = await self._api_client.get_assessment_context(credentials)
                processing_stage = "context_validation"
                self._validate_context(claim, context)
                image_path = workspace.file("input-image")
                processing_stage = "image_download"
                downloaded_bytes = await self._api_client.download_input(
                    credentials,
                    image_path,
                    claim.limits.maximum_input_bytes,
                )
                if downloaded_bytes != context.image_size_bytes:
                    raise WorkerApiContractError(
                        "Downloaded image size does not match vision context"
                    )

                processing_stage = "image_optimization"
                model_image_path = workspace.file("model-input.jpg")
                image_diagnostics = optimize_for_vision(
                    image_path,
                    model_image_path,
                    maximum_edge_pixels=self._settings.maximum_image_edge_pixels,
                    jpeg_quality=self._settings.image_jpeg_quality,
                )

                processing_stage = "prompt_build"
                rescue_transcription_requested = (
                    self._settings.prompt_version == "vision-ocr-v7"
                    and requires_rescue_transcription(context.raw_ocr_text)
                )
                prompt = build_vision_prompt(context, self._settings.prompt_version)
                logger.info(
                    "vision_assessment_started",
                    extra={
                        "job_id": claim.job_id,
                        "document_page_id": context.document_page_id,
                        "ocr_result_id": context.ocr_result_id,
                        "model": self._settings.vision_model,
                        "prompt_characters": len(prompt),
                        "ocr_characters": len(context.raw_ocr_text),
                        "image_width": context.image_width,
                        "image_height": context.image_height,
                        "image_bytes": downloaded_bytes,
                        "model_image_width": image_diagnostics.width,
                        "model_image_height": image_diagnostics.height,
                        "model_image_bytes": image_diagnostics.size_bytes,
                        "image_resized": image_diagnostics.resized,
                    },
                )

                processing_stage = "model_resolution"
                model_version = await self._ollama_client.resolve_model_version()
                try:
                    processing_stage = "ollama_response"
                    model_call = await self._ollama_client.assess(
                        model_image_path,
                        prompt,
                        allow_rescue_transcription=rescue_transcription_requested,
                        maximum_issues=3,
                    )
                except VisionContractError as error:
                    if error.stage != "response_truncated":
                        raise
                    logger.warning(
                        "vision_response_truncated_compact_retry",
                        extra={
                            "job_id": claim.job_id,
                            "document_page_id": context.document_page_id,
                            "validation_stage": error.stage,
                            "exception_type": type(error).__name__,
                        },
                    )
                    processing_stage = "ollama_compact_retry"
                    model_call = await self._ollama_client.assess(
                        model_image_path,
                        build_compact_retry_prompt(context),
                        allow_rescue_transcription=False,
                        maximum_issues=2,
                        maximum_tokens=768,
                    )
                    compact_retry_used = True
                else:
                    compact_retry_used = False

                processing_stage = "preparation"
                prepared = prepare_model_result(
                    model_call.result,
                    context.raw_ocr_text,
                    claim.limits,
                    allow_rescue_transcription=rescue_transcription_requested,
                )
                if prepared.failed_validation_rule is not None:
                    validation_codes = prepared.rejected_issue_stages or (
                        prepared.failed_validation_rule,
                    )
                    for validation_code in sorted(set(validation_codes)):
                        logger.warning(
                            "vision_suggestion_rejected",
                            extra={
                                "job_id": claim.job_id,
                                "document_page_id": context.document_page_id,
                                "validation_stage": validation_code,
                                "mapping_validation_code": validation_code,
                                "exception_type": "VisionContractError",
                            },
                        )

                processing_stage = "mapping"
                request = build_assessment_request(
                    prepared.result,
                    model_version,
                    self._settings,
                    claim.limits,
                    suggested_text=prepared.suggested_text,
                    outcome=prepared.outcome,
                    diagnostics=model_call.diagnostics,
                    compact_retry_used=compact_retry_used,
                    rejected_suggestion_stage=prepared.failed_validation_rule,
                    rejected_issue_stages=prepared.rejected_issue_stages,
                )
                processing_stage = "submission"
                response = await self._api_client.submit_assessment(
                    credentials,
                    request,
                )
                processing_stage = "submission_validation"
                self._validate_response(claim, context, response)
                return processing_stage
        except Exception as error:
            try:
                setattr(error, "processing_stage", processing_stage)
            except (AttributeError, TypeError):
                pass
            raise

    @staticmethod
    def _validate_claim(claim: ClaimResponse) -> None:
        if claim.job_type is not WorkerJobType.VISION_OCR_ASSESSMENT:
            raise WorkerApiContractError("Vision operation requires a VISION_OCR_ASSESSMENT job")

        if (
                claim.target.document_id is None
                or claim.target.document_page_id is None
                or not claim.target.input_available
        ):
            raise ValueError("Vision claim has no page image input")

    @staticmethod
    def _validate_context(
            claim: ClaimResponse,
            context: VisionAssessmentContext,
    ) -> None:
        if context.job_id != claim.job_id:
            raise WorkerApiContractError("Vision context identifies a different job")

        if context.document_id != claim.target.document_id:
            raise WorkerApiContractError("Vision context identifies a different document")

        if context.document_page_id != claim.target.document_page_id:
            raise WorkerApiContractError("Vision context identifies a different page")

        if context.image_size_bytes > claim.limits.maximum_input_bytes:
            raise WorkerApiContractError("Vision image exceeds the backend size limit")

        if len(context.raw_ocr_text) > claim.limits.maximum_ocr_text_characters:
            raise WorkerApiContractError("Vision OCR text exceeds the backend limit")

        if (
                context.image_width is not None
                and context.image_width > claim.limits.maximum_pixel_width
        ):
            raise WorkerApiContractError("Vision image exceeds the maximum width")

        if (
                context.image_height is not None
                and context.image_height > claim.limits.maximum_pixel_height
        ):
            raise WorkerApiContractError("Vision image exceeds the maximum height")

        if (
                context.image_width is not None
                and context.image_height is not None
                and context.image_width * context.image_height
                > claim.limits.maximum_page_pixels
        ):
            raise WorkerApiContractError("Vision image exceeds the maximum pixel count")

    @staticmethod
    def _validate_response(
            claim: ClaimResponse,
            context: VisionAssessmentContext,
            response: VisionAssessmentResponse,
    ) -> None:
        if response.job_id != claim.job_id:
            raise WorkerApiContractError("Vision response identifies a different job")

        if response.document_id != context.document_id:
            raise WorkerApiContractError("Vision response identifies a different document")

        if response.page_id != context.document_page_id:
            raise WorkerApiContractError("Vision response identifies a different page")

        if response.ocr_result_id != context.ocr_result_id:
            raise WorkerApiContractError("Vision response identifies a different OCR result")

        if response.input_media_id != context.input_media_id:
            raise WorkerApiContractError("Vision response identifies different input media")


class VisionAssessmentProcessor:
    def __init__(
            self,
            *,
            api_client: VisionWorkerApiClient,
            operation: VisionAssessmentOperation
    ) -> None:
        self._api_client = api_client
        self._operation = operation

    async def process(self, claim: ClaimResponse) -> None:
        async def run_operation(credentials: ClaimCredentials) -> None:
            await self._operation.run(claim, credentials)

        await execute_claimed_job(
            api_client=self._api_client,
            claim=claim,
            operation=run_operation,
        )
