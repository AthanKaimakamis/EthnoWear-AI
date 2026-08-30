import asyncio

import pytest

from ethnowear_document_worker.api.models import WorkerJobType
from ethnowear_document_worker.jobs.dispatcher import JobDispatcher
from test_ocr_job import claim as make_claim


class RecordingProcessor:
    def __init__(self) -> None:
        self.claims = []

    async def process(self, claim) -> None:
        self.claims.append(claim)


@pytest.mark.parametrize(
    ("job_type", "expected_processor"),
    [
        (WorkerJobType.PAGE_EXTRACTION, "page"),
        (WorkerJobType.OCR, "ocr"),
    ],
)
def test_dispatcher_routes_claim_to_matching_processor(
    job_type: WorkerJobType,
    expected_processor: str,
) -> None:
    page = RecordingProcessor()
    ocr = RecordingProcessor()
    dispatcher = JobDispatcher(
        page_extraction=page,
        ocr=ocr,
    )
    job = make_claim()
    job.job_type = job_type

    asyncio.run(dispatcher.process(job))

    assert page.claims == ([job] if expected_processor == "page" else [])
    assert ocr.claims == ([job] if expected_processor == "ocr" else [])


def test_dispatcher_rejects_job_type_without_registered_processor() -> None:
    page = RecordingProcessor()
    ocr = RecordingProcessor()
    dispatcher = JobDispatcher(
        page_extraction=page,
        ocr=ocr,
    )
    dispatcher._processor.pop(WorkerJobType.OCR)

    with pytest.raises(ValueError, match="unsupported job type"):
        asyncio.run(dispatcher.process(make_claim()))

    assert page.claims == []
    assert ocr.claims == []
