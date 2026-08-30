from pathlib import Path

import cv2
import numpy as np
import pytest

from ethnowear_figure_worker.crops.cropper import (
    FigureCropError,
    create_figure_crops,
)
from ethnowear_figure_worker.inspection.models import (
    CandidateDecision,
    CandidateRejectionReason,
)
from ethnowear_figure_worker.layout.models import PixelBounds


def accepted(
    *,
    candidate_id: int = 1,
    ordinal: int = 1,
    bounds: PixelBounds = PixelBounds(40, 40, 80, 80),
    caption: str | None = "Обр. 12 — Престилка",
) -> CandidateDecision:
    return CandidateDecision(
        candidate_id=candidate_id,
        candidate_ordinal=ordinal,
        bounds=bounds,
        accepted=True,
        confidence=0.85,
        rejection_reason=None,
        raw_caption_text=caption,
    )


def rejected() -> CandidateDecision:
    return CandidateDecision(
        candidate_id=2,
        candidate_ordinal=2,
        bounds=PixelBounds(10, 10, 20, 20),
        accepted=False,
        confidence=0.0,
        rejection_reason=CandidateRejectionReason.AREA_TOO_SMALL,
        raw_caption_text=None,
    )


def save_page(path: Path) -> None:
    rng = np.random.default_rng(42)
    image = rng.integers(
        0,
        256,
        size=(200, 200, 3),
        dtype=np.uint8,
    )
    assert cv2.imwrite(str(path), image)


def create(
    source: Path,
    destination: Path,
    decisions: tuple[CandidateDecision, ...],
    *,
    maximum_crop_bytes: int = 100_000,
):
    return create_figure_crops(
        source,
        destination,
        decisions,
        margin_ratio=0.05,
        jpeg_quality=95,
        maximum_crop_bytes=maximum_crop_bytes,
        maximum_caption_characters=2_000,
        maximum_printed_number_characters=100,
    )


def test_creates_full_resolution_bounded_crop(tmp_path: Path) -> None:
    source = tmp_path / "page.png"
    destination = tmp_path / "crops"
    save_page(source)

    artifacts = create(source, destination, (accepted(), rejected()))

    assert len(artifacts) == 1
    artifact = artifacts[0]
    assert artifact.page_bounds == PixelBounds(30, 30, 100, 100)
    assert artifact.crop_width == 100
    assert artifact.crop_height == 100
    assert artifact.candidate_confidence == 0.85
    assert artifact.raw_caption_text == "Обр. 12 — Престилка"
    assert artifact.printed_figure_number == "12"
    assert artifact.content_type == "image/jpeg"
    assert artifact.path.stat().st_size == artifact.encoded_bytes
    assert artifact.encoded_bytes <= 100_000
    assert artifact.path.stat().st_mode & 0o777 == 0o600


def test_zero_accepted_candidates_produces_no_files(tmp_path: Path) -> None:
    source = tmp_path / "page.png"
    destination = tmp_path / "crops"
    save_page(source)

    assert create(source, destination, (rejected(),)) == ()
    assert list(destination.iterdir()) == []


def test_margin_is_clamped_to_page_bounds(tmp_path: Path) -> None:
    source = tmp_path / "page.png"
    save_page(source)

    artifacts = create(
        source,
        tmp_path / "crops",
        (accepted(bounds=PixelBounds(0, 0, 40, 40)),),
    )

    assert artifacts[0].page_bounds == PixelBounds(0, 0, 50, 50)


def test_caption_is_bounded_without_inventing_content(tmp_path: Path) -> None:
    source = tmp_path / "page.png"
    save_page(source)

    artifacts = create_figure_crops(
        source,
        tmp_path / "crops",
        (accepted(caption="  Обр. 12 — Престилка  "),),
        margin_ratio=0.0,
        jpeg_quality=90,
        maximum_crop_bytes=100_000,
        maximum_caption_characters=7,
        maximum_printed_number_characters=100,
    )

    assert artifacts[0].raw_caption_text == "Обр. 12"
    assert artifacts[0].printed_figure_number == "12"


def test_fails_when_crop_cannot_fit_byte_limit(tmp_path: Path) -> None:
    source = tmp_path / "page.png"
    destination = tmp_path / "crops"
    save_page(source)

    with pytest.raises(FigureCropError, match="maximum encoded size"):
        create(
            source,
            destination,
            (accepted(),),
            maximum_crop_bytes=10,
        )

    assert list(destination.iterdir()) == []


def test_does_not_overwrite_existing_temporary_crop(tmp_path: Path) -> None:
    source = tmp_path / "page.png"
    destination = tmp_path / "crops"
    save_page(source)
    destination.mkdir()
    existing = destination / "figure-1.jpg"
    existing.write_bytes(b"existing")

    with pytest.raises(FigureCropError, match="already exists"):
        create(source, destination, (accepted(),))

    assert existing.read_bytes() == b"existing"


def test_removes_created_crops_after_later_failure(tmp_path: Path) -> None:
    source = tmp_path / "page.png"
    destination = tmp_path / "crops"
    save_page(source)
    destination.mkdir()
    existing = destination / "figure-2.jpg"
    existing.write_bytes(b"existing")

    with pytest.raises(FigureCropError, match="already exists"):
        create(
            source,
            destination,
            (
                accepted(candidate_id=1, ordinal=1),
                accepted(candidate_id=2, ordinal=2),
            ),
        )

    assert not (destination / "figure-1.jpg").exists()
    assert existing.read_bytes() == b"existing"


def test_rejects_invalid_limits_before_decoding(tmp_path: Path) -> None:
    source = tmp_path / "missing.png"

    with pytest.raises(ValueError, match="margin ratio"):
        create_figure_crops(
            source,
            tmp_path / "crops",
            (),
            margin_ratio=0.11,
            jpeg_quality=95,
            maximum_crop_bytes=100_000,
            maximum_caption_characters=2_000,
            maximum_printed_number_characters=100,
        )


def test_rejects_undecodable_source(tmp_path: Path) -> None:
    source = tmp_path / "page.png"
    source.write_bytes(b"not an image")

    with pytest.raises(FigureCropError, match="could not be decoded"):
        create(source, tmp_path / "crops", ())
