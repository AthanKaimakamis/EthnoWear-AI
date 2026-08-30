import asyncio
from pathlib import Path

from ethnowear_worker_common.api.errors import WorkerInputError
from ethnowear_worker_common.api.models import ClaimResponse, WorkerJobType
from ethnowear_worker_common.jobs.context import ClaimCredentials
from ethnowear_worker_common.temp import JobWorkspace

from ethnowear_quality_worker.api.client import QualityWorkerApiClient
from ethnowear_quality_worker.input.image import OcrImageError, inspect_ocr_image
from ethnowear_quality_worker.jobs.execution import execute_claimed_job
from ethnowear_quality_worker.jobs.validation import (
    validate_quality_context,
    validate_quality_response,
)
from ethnowear_quality_worker.quality.dictionary import BulgarianDictionary
from ethnowear_quality_worker.quality.evaluator import evaluate_quality


class QualityAssessmentProcessor:
    def __init__(
        self,
        *,
        api_client: QualityWorkerApiClient,
        temporary_root: Path,
        dictionary: BulgarianDictionary | None = None,
    ) -> None:
        self._api_client = api_client
        self._temporary_root = temporary_root
        self._dictionary = dictionary

    async def process(self, claim: ClaimResponse) -> None:
        if claim.job_type is not WorkerJobType.OCR_QUALITY_ASSESSMENT:
            raise ValueError(
                "Quality processor requires an OCR quality-assessment job"
            )
        if (
            claim.target.document_id is None
            or claim.target.document_page_id is None
            or not claim.target.input_available
        ):
            raise ValueError("Quality claim has no page image input")

        async def operation(credentials: ClaimCredentials) -> None:
            with JobWorkspace.create(
                self._temporary_root,
                claim.job_id,
                claim.attempt,
            ) as workspace:
                context = await self._api_client.get_quality_assessment_context(
                    credentials
                )
                input_image = workspace.path / "input-image"
                try:
                    await self._api_client.download_input(
                        credentials,
                        input_image,
                        claim.limits.maximum_input_bytes,
                    )
                except WorkerInputError as error:
                    raise OcrImageError("Quality input download was invalid") from error
                image = await asyncio.to_thread(
                    inspect_ocr_image,
                    input_image,
                    maximum_width=claim.limits.maximum_pixel_width,
                    maximum_height=claim.limits.maximum_pixel_height,
                    maximum_pixels=claim.limits.maximum_page_pixels,
                )
                validate_quality_context(context, claim, image)
                assessment = await asyncio.to_thread(
                    evaluate_quality,
                    context,
                    image,
                    claim.limits,
                    self._dictionary,
                )
                response = await self._api_client.submit_quality_assessment(
                    credentials,
                    assessment,
                )
                validate_quality_response(response, context, claim)

        await execute_claimed_job(
            api_client=self._api_client,
            claim=claim,
            operation=operation,
        )
