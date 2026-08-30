import json
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import AsyncMock

import cv2
import numpy as np
from pydantic import SecretStr
import pytest

from ethnowear_worker_common.api.errors import WorkerApiContractError
from ethnowear_worker_common.api.models import WorkerJobType
from ethnowear_worker_common.jobs.context import ClaimCredentials

from ethnowear_figure_worker.api.models import (
    FigureCropResponse,
    FigureExtractionContextResponse,
)
from ethnowear_figure_worker.config import FigureWorkerSettings
from ethnowear_figure_worker.jobs import processor as processor_module
from ethnowear_figure_worker.jobs.processor import FigureExtractionProcessor


def claim() -> SimpleNamespace:
    return SimpleNamespace(
        job_id=41,
        job_type=WorkerJobType.EXTRACT_PAGE_FIGURES,
        attempt=1,
        target=SimpleNamespace(
            document_id=30,
            document_page_id=98,
            input_available=True,
        ),
        limits=SimpleNamespace(
            maximum_input_bytes=2_000_000,
            maximum_pixel_width=1_000,
            maximum_pixel_height=1_000,
            maximum_page_pixels=1_000_000,
            maximum_ocr_context_bytes=100_000,
            maximum_figure_candidates=10,
            maximum_figure_caption_characters=2_000,
            maximum_printed_figure_number_characters=100,
            maximum_figure_crop_bytes=500_000,
        ),
    )


def context(
    *,
    candidate_count: int = 1,
) -> FigureExtractionContextResponse:
    candidates = []
    blocks = []

    for index in range(candidate_count):
        left = 0.10 + index * 0.45
        candidates.append({
            "candidateId": 801 + index,
            "candidateOrdinal": index + 1,
            "normalizedX": left,
            "normalizedY": 0.20,
            "normalizedWidth": 0.30,
            "normalizedHeight": 0.40,
            "rawCaptionText": f"Figure {index + 1}",
            "detectionConfidence": 0.87,
        })
        blocks.append({
            "id": index + 1,
            "type": "ILLUSTRATION",
            "readingOrder": index,
            "left": round(left * 200),
            "top": 40,
            "width": 60,
            "height": 80,
            "confidence": 0.87,
            "text": "",
            "wordCount": 0,
        })

    return FigureExtractionContextResponse.model_validate({
        "jobId": 41,
        "documentId": 30,
        "documentPageId": 98,
        "documentPageMediaId": 501,
        "inputMediaAssetId": 601,
        "ocrResultId": 701,
        "ocrLayoutJson": json.dumps({
            "schemaVersion": 2,
            "selectedStrategy": "REGION_PSM_6",
            "blocks": blocks,
            "words": [],
        }),
        "candidates": candidates,
        "maximumCaptionCharacters": 2_000,
        "maximumPrintedNumberCharacters": 100,
        "maximumCropBytes": 500_000,
    })


def settings(tmp_path: Path) -> FigureWorkerSettings:
    return FigureWorkerSettings(
        api_base_url="http://localhost:8080",
        worker_id="figure-worker-test",
        api_token="x" * 32,
        temporary_root=tmp_path / "workspaces",
        crop_margin_ratio=0.01,
        maximum_local_candidates=10,
        jpeg_quality=90,
    )


def textured_page() -> np.ndarray:
    rng = np.random.default_rng(42)
    return rng.integers(
        0,
        256,
        size=(200, 200, 3),
        dtype=np.uint8,
    )


async def install_execution_stub(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    async def execute_stub(
        *,
        api_client: object,
        claim: object,
        operation,
    ) -> None:
        del api_client
        await operation(ClaimCredentials(
            job_id=claim.job_id,
            claim_token=SecretStr("claim-token"),
        ))

    monkeypatch.setattr(
        processor_module,
        "execute_claimed_job",
        execute_stub,
    )


def api_client_with_image(
    extraction_context: FigureExtractionContextResponse,
    image: np.ndarray,
) -> SimpleNamespace:
    api_client = SimpleNamespace(
        get_context=AsyncMock(return_value=extraction_context),
        download_input=AsyncMock(),
        upload_figure=AsyncMock(return_value=FigureCropResponse(
            figure_id=901,
            document_page_id=98,
            media_asset_id=1_001,
            figure_ordinal=1,
            existing=False,
        )),
    )

    async def download(
        _credentials: object,
        destination: Path,
        _maximum_bytes: int,
    ) -> int:
        encoded, content = cv2.imencode(".png", image)
        assert encoded
        destination.write_bytes(content.tobytes())
        return destination.stat().st_size

    api_client.download_input.side_effect = download
    return api_client


@pytest.mark.asyncio
async def test_processes_and_uploads_confirmed_figure(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    await install_execution_stub(monkeypatch)
    api_client = api_client_with_image(context(), textured_page())
    worker_settings = settings(tmp_path)

    await FigureExtractionProcessor(
        api_client=api_client,
        settings=worker_settings,
    ).process(claim())

    api_client.upload_figure.assert_awaited_once()
    metadata = api_client.upload_figure.await_args.args[1]
    crop_path = api_client.upload_figure.await_args.args[2]
    assert metadata.candidate_id == 801
    assert metadata.printed_figure_number == "1"
    assert crop_path.name == "figure-1.jpg"
    assert not crop_path.exists()
    assert list(worker_settings.temporary_root.iterdir()) == []


@pytest.mark.asyncio
async def test_zero_confirmed_figures_completes_without_upload(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    await install_execution_stub(monkeypatch)
    api_client = api_client_with_image(
        context(),
        np.full((200, 200, 3), 255, dtype=np.uint8),
    )

    await FigureExtractionProcessor(
        api_client=api_client,
        settings=settings(tmp_path),
    ).process(claim())

    api_client.upload_figure.assert_not_awaited()


@pytest.mark.asyncio
async def test_upload_failure_removes_workspace(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    await install_execution_stub(monkeypatch)
    api_client = api_client_with_image(
        context(candidate_count=2),
        textured_page(),
    )
    api_client.upload_figure.side_effect = RuntimeError("upload failed")
    worker_settings = settings(tmp_path)

    with pytest.raises(RuntimeError, match="upload failed") as failure:
        await FigureExtractionProcessor(
            api_client=api_client,
            settings=worker_settings,
        ).process(claim())

    assert failure.value.processing_stage == "crop_upload"
    assert list(worker_settings.temporary_root.iterdir()) == []


@pytest.mark.asyncio
async def test_rejects_upload_response_for_different_page(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    await install_execution_stub(monkeypatch)
    api_client = api_client_with_image(context(), textured_page())
    api_client.upload_figure.return_value = FigureCropResponse(
        figure_id=901,
        document_page_id=99,
        media_asset_id=1_001,
        figure_ordinal=1,
        existing=True,
    )

    with pytest.raises(WorkerApiContractError, match="different page"):
        await FigureExtractionProcessor(
            api_client=api_client,
            settings=settings(tmp_path),
        ).process(claim())


@pytest.mark.parametrize(
    ("attribute", "value", "message"),
    [
        ("job_id", 42, "different job"),
        ("document_id", 31, "different document"),
        ("document_page_id", 99, "different page"),
        ("maximum_caption_characters", 2_001, "caption limit"),
        ("maximum_printed_number_characters", 101, "printed-number limit"),
        ("maximum_crop_bytes", 500_001, "crop-size limit"),
    ],
)
def test_rejects_context_contract_mismatch(
    tmp_path: Path,
    attribute: str,
    value: object,
    message: str,
) -> None:
    extraction_context = context()
    object.__setattr__(extraction_context, attribute, value)
    worker = FigureExtractionProcessor(
        api_client=SimpleNamespace(),  # type: ignore[arg-type]
        settings=settings(tmp_path),
    )

    with pytest.raises(WorkerApiContractError, match=message):
        worker._validate_context(  # noqa: SLF001
            claim(),  # type: ignore[arg-type]
            extraction_context,
        )


def test_rejects_wrong_job_type(tmp_path: Path) -> None:
    invalid_claim = claim()
    invalid_claim.job_type = WorkerJobType.OCR
    worker = FigureExtractionProcessor(
        api_client=SimpleNamespace(),  # type: ignore[arg-type]
        settings=settings(tmp_path),
    )

    with pytest.raises(WorkerApiContractError, match="requires"):
        worker._validate_claim(invalid_claim)  # noqa: SLF001
