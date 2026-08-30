from ethnowear_worker_common.api.models import (
    ClaimResponse,
    WorkerJobType
)
from ethnowear_worker_common.jobs.runner import JobProcessor


class JobDispatcher:
    def __init__(
            self,
            *,
            page_extraction: JobProcessor,
            ocr: JobProcessor,
    ) -> None:
        self._processor = {
            WorkerJobType.PAGE_EXTRACTION: page_extraction,
            WorkerJobType.OCR: ocr,
        }

    async def process(self, claim: ClaimResponse) -> None:
        processor = self._processor.get(claim.job_type)

        if processor is None:
            raise ValueError("Claim contains an unsupported job type.")

        await processor.process(claim)
