import asyncio
from pathlib import Path

from ethnowear_document_worker.api.client import WorkerApiClient
from ethnowear_worker_common.api.models import ClaimResponse, WorkerJobType
from ethnowear_worker_common.jobs.context import ClaimCredentials
from ethnowear_document_worker.jobs.execution import execute_claimed_job
from ethnowear_document_worker.jobs.ocr import (
    build_ocr_result,
    validate_ocr_result_response,
)
from ethnowear_worker_common.api.errors import WorkerInputError
from ethnowear_document_worker.ocr.image import (
    OcrImageError,
    apply_rotation,
    inspect_ocr_image,
)
from ethnowear_document_worker.ocr.tesseract import TesseractRunner
from ethnowear_document_worker.ocr.pipeline import OcrPipeline
from ethnowear_document_worker.temp.workspace import JobWorkspace


class OcrProcessor:
    def __init__(
            self,
            *,
            api_client: WorkerApiClient,
            temporary_root: Path,
            tesseract: TesseractRunner,
            oem: int,
            psm: int,
            maximum_regions: int = 32,
    ) -> None:
        self._api_client = api_client
        self._temporary_root = temporary_root
        self._tesseract = tesseract
        self._oem = oem
        self._psm = psm
        self._pipeline = OcrPipeline(
            tesseract,
            maximum_regions=maximum_regions,
        )

    async def process(self, claim: ClaimResponse) -> None:
        if claim.job_type is not WorkerJobType.OCR:
            raise ValueError("OCR processor requires an OCR job")

        if (claim.target.document_id is None
                or claim.target.document_page_id is None
                or not claim.target.input_available):
            raise ValueError("OCR claim has no page image input")

        async def operation(credentials: ClaimCredentials) -> None:
            with JobWorkspace.create(
                    self._temporary_root,
                    claim.job_id,
                    claim.attempt,
            ) as workspace:
                try:
                    await self._api_client.download_ocr_input(
                        credentials,
                        workspace.input_image,
                        claim.limits.maximum_input_bytes,
                    )
                except WorkerInputError as error:
                    raise OcrImageError("OCR input download was invalid") from error

                await asyncio.to_thread(
                    inspect_ocr_image,
                    workspace.input_image,
                    maximum_width=claim.limits.maximum_pixel_width,
                    maximum_height=claim.limits.maximum_pixel_height,
                    maximum_pixels=claim.limits.maximum_page_pixels,
                )

                rotation = await self._tesseract.detect_rotation(
                    workspace.input_image
                )
                preprocessed = await asyncio.to_thread(
                    apply_rotation,
                    workspace.input_image,
                    workspace.preprocessed_image,
                    rotation,
                )
                ocr_input = (
                    workspace.preprocessed_image
                    if preprocessed
                    else workspace.input_image
                )

                if preprocessed:
                    await asyncio.to_thread(
                        inspect_ocr_image,
                        ocr_input,
                        maximum_width=claim.limits.maximum_pixel_width,
                        maximum_height=claim.limits.maximum_pixel_height,
                        maximum_pixels=claim.limits.maximum_page_pixels,
                    )

                output = await self._pipeline.recognize_page(
                    ocr_input,
                    workspace.path,
                    claim.limits.maximum_ocr_output_bytes,
                    maximum_figure_candidates=(
                        claim.limits.maximum_figure_candidates
                    ),
                    maximum_figure_caption_characters=(
                        claim.limits.maximum_figure_caption_characters
                    ),
                )

                request = build_ocr_result(
                    output,
                    maximum_text_characters=(
                        claim.limits.maximum_ocr_text_characters
                    ),
                    maximum_output_bytes=(
                        claim.limits.maximum_ocr_output_bytes
                    ),
                    oem=self._oem,
                    psm=self._psm,
                    preprocessed=preprocessed,
                )

                response = await self._api_client.submit_ocr_result(
                    credentials,
                    request,
                )

                validate_ocr_result_response(
                    response,
                    claim,
                )

        await execute_claimed_job(
            api_client=self._api_client,
            claim=claim,
            operation=operation,
        )
