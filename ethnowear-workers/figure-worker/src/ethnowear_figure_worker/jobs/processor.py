import asyncio
import logging
from ethnowear_worker_common.api.errors import (
    WorkerApiContractError,
)
from ethnowear_worker_common.api.models import (
    ClaimResponse,
    WorkerJobType,
)
from ethnowear_worker_common.jobs.context import ClaimCredentials
from ethnowear_worker_common.temp import JobWorkspace

from ethnowear_figure_worker.api.client import FigureWorkerApiClient
from ethnowear_figure_worker.api.models import (
    FigureCropRequest,
    FigureExtractionContextResponse,
)
from ethnowear_figure_worker.config import FigureWorkerSettings
from ethnowear_figure_worker.crops.cropper import (
    create_figure_crops,
)
from ethnowear_figure_worker.inspection.image import (
    inspect_figure_image,
)
from ethnowear_figure_worker.inspection.reevaluator import (
    reevaluate_candidates,
)
from ethnowear_figure_worker.jobs.execution import execute_claimed_job
from ethnowear_figure_worker.layout.parser import parse_ocr_layout

logger = logging.getLogger(__name__)


class FigureExtractionProcessor:
    def __init__(
            self,
            *,
            api_client: FigureWorkerApiClient,
            settings: FigureWorkerSettings,
    ) -> None:
        self._api_client = api_client
        self._settings = settings

    async def process(self, claim: ClaimResponse) -> None:
        self._validate_claim(claim)

        async def operation(credentials: ClaimCredentials) -> None:
            await self._run_pipeline(claim, credentials)

        await execute_claimed_job(
            api_client=self._api_client,
            claim=claim,
            operation=operation,
        )

    async def _run_pipeline(
            self,
            claim: ClaimResponse,
            credentials: ClaimCredentials,
    ) -> None:
        processing_stage = "workspace"

        try:
            with JobWorkspace.create(
                    self._settings.temporary_root,
                    claim.job_id,
                    claim.attempt,
            ) as workspace:
                processing_stage = "context_fetch"
                context = await self._api_client.get_context(credentials)

                processing_stage = "context_validation"
                self._validate_context(claim, context)

                input_path = workspace.file("page-image")

                processing_stage = "image_download"
                downloaded_bytes = (
                    await self._api_client.download_input(
                        credentials,
                        input_path,
                        claim.limits.maximum_input_bytes,
                    )
                )

                processing_stage = "image_inspection"
                image_info = await asyncio.to_thread(
                    inspect_figure_image,
                    input_path,
                    maximum_width=claim.limits.maximum_pixel_width,
                    maximum_height=claim.limits.maximum_pixel_height,
                    maximum_pixels=claim.limits.maximum_page_pixels,
                )

                processing_stage = "layout_parsing"
                layout = await asyncio.to_thread(
                    parse_ocr_layout,
                    context.ocr_layout_json,
                    maximum_bytes=claim.limits.maximum_ocr_context_bytes,
                )

                maximum_candidates = min(
                    self._settings.maximum_local_candidates,
                    claim.limits.maximum_figure_candidates,
                )

                processing_stage = "candidate_reevaluation"
                decisions = await asyncio.to_thread(
                    reevaluate_candidates,
                    input_path,
                    context.candidates,
                    layout,
                    maximum_candidates=maximum_candidates,
                )

                processing_stage = "crop_creation"
                crop_directory = workspace.path / "crops"
                artifacts = await asyncio.to_thread(
                    create_figure_crops,
                    input_path,
                    crop_directory,
                    decisions,
                    margin_ratio=self._settings.crop_margin_ratio,
                    jpeg_quality=self._settings.jpeg_quality,
                    maximum_crop_bytes=context.maximum_crop_bytes,
                    maximum_caption_characters=context.maximum_caption_characters,
                    maximum_printed_number_characters=context.maximum_printed_number_characters,
                )

                logger.info(
                    "figure_candidates_evaluated",
                    extra={
                        "job_id": claim.job_id,
                        "document_page_id": context.document_page_id,
                        "image_width": image_info.width,
                        "image_height": image_info.height,
                        "image_bytes": downloaded_bytes,
                        "candidate_count": len(context.candidates),
                        "accepted_count": len(artifacts),
                        "rejected_count": len(context.candidates) - len(artifacts),
                    },
                )

                processing_stage = "crop_upload"
                for artifact in artifacts:
                    response = (
                        await self._api_client.upload_figure(
                            credentials,
                            FigureCropRequest(
                                candidate_id=artifact.candidate_id,
                                figure_ordinal=artifact.figure_ordinal,
                                printed_figure_number=artifact.printed_figure_number,
                                raw_caption_text=artifact.raw_caption_text,
                            ),
                            artifact.path,
                            content_type=artifact.content_type,
                        )
                    )

                    if response.document_page_id != context.document_page_id:
                        raise WorkerApiContractError("Figure upload response identifies a different page")

                logger.info(
                    "figure_crops_uploaded",
                    extra={
                        "job_id": claim.job_id,
                        "document_page_id": (
                            context.document_page_id
                        ),
                        "uploaded_count": len(artifacts),
                    },
                )

        except Exception as error:
            try:
                setattr(
                    error,
                    "processing_stage",
                    processing_stage,
                )
            except (AttributeError, TypeError):
                pass

            raise

    @staticmethod
    def _validate_claim(claim: ClaimResponse) -> None:
        if claim.job_type is not WorkerJobType.EXTRACT_PAGE_FIGURES:
            raise WorkerApiContractError("Figure processor requires an EXTRACT_PAGE_FIGURES job")

        if (
                claim.target.document_id is None
                or claim.target.document_page_id is None
                or not claim.target.input_available
        ):
            raise WorkerApiContractError("Figure claim has no page image input")

    def _validate_context(
            self,
            claim: ClaimResponse,
            context: FigureExtractionContextResponse,
    ) -> None:
        if context.job_id != claim.job_id:
            raise WorkerApiContractError("Figure context identifies a different job")

        if context.document_id != claim.target.document_id:
            raise WorkerApiContractError("Figure context identifies a different document")

        if context.document_page_id != claim.target.document_page_id:
            raise WorkerApiContractError("Figure context identifies a different page")

        maximum_candidates = min(
            self._settings.maximum_local_candidates,
            claim.limits.maximum_figure_candidates,
        )

        if len(context.candidates) > maximum_candidates:
            raise WorkerApiContractError("Figure context exceeds the candidate limit")

        if context.maximum_caption_characters > claim.limits.maximum_figure_caption_characters:
            raise WorkerApiContractError("Figure context exceeds the caption limit")

        if context.maximum_printed_number_characters > claim.limits.maximum_printed_figure_number_characters:
            raise WorkerApiContractError("Figure context exceeds the printed-number limit")

        if context.maximum_crop_bytes > claim.limits.maximum_figure_crop_bytes:
            raise WorkerApiContractError("Figure context exceeds the crop-size limit")
