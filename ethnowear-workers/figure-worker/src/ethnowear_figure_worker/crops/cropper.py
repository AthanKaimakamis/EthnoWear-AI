from pathlib import Path

import cv2

from ethnowear_figure_worker.crops.caption import (
    extract_printed_figure_number,
)
from ethnowear_figure_worker.crops.models import FigureCropArtifact
from ethnowear_figure_worker.inspection.models import CandidateDecision
from ethnowear_figure_worker.layout.models import PixelBounds


class FigureCropError(ValueError):
    pass


def create_figure_crops(
        source: Path,
        destination_directory: Path,
        decisions: tuple[CandidateDecision, ...],
        *,
        margin_ratio: float,
        jpeg_quality: int,
        maximum_crop_bytes: int,
        maximum_caption_characters: int,
        maximum_printed_number_characters: int,
) -> tuple[FigureCropArtifact, ...]:
    _validate_limits(
        margin_ratio=margin_ratio,
        jpeg_quality=jpeg_quality,
        maximum_crop_bytes=maximum_crop_bytes,
        maximum_caption_characters=maximum_caption_characters,
        maximum_printed_number_characters=(
            maximum_printed_number_characters
        ),
    )

    image = cv2.imread(str(source), cv2.IMREAD_COLOR)
    if image is None:
        raise FigureCropError("Figure input could not be decoded")

    page_height, page_width = image.shape[:2]
    destination_directory.mkdir(
        mode=0o700,
        parents=True,
        exist_ok=True,
    )

    artifacts: list[FigureCropArtifact] = []

    try:
        for decision in decisions:
            if not decision.accepted:
                continue

            if decision.bounds is None:
                raise FigureCropError("Accepted figure candidate has no bounds")

            crop_bounds = _add_margin(
                decision.bounds,
                page_width,
                page_height,
                margin_ratio,
            )
            crop = image[
                crop_bounds.top:crop_bounds.bottom,
                crop_bounds.left:crop_bounds.right,
            ]

            if crop.size == 0:
                raise FigureCropError(
                    "Figure crop is empty"
                )

            encoded = _encode_bounded_jpeg(
                crop,
                initial_quality=jpeg_quality,
                maximum_bytes=maximum_crop_bytes,
            )

            destination = (
                    destination_directory
                    / f"figure-{decision.candidate_ordinal}.jpg"
            )

            try:
                with destination.open("xb") as output:
                    destination.chmod(0o600)
                    output.write(encoded)
            except FileExistsError as error:
                raise FigureCropError("Temporary figure crop already exists") from error

            caption = _bounded_caption(
                decision.raw_caption_text,
                maximum_caption_characters,
            )

            artifacts.append(FigureCropArtifact(
                candidate_id=decision.candidate_id,
                figure_ordinal=decision.candidate_ordinal,
                path=destination,
                content_type="image/jpeg",
                page_bounds=crop_bounds,
                crop_width=crop.shape[1],
                crop_height=crop.shape[0],
                candidate_confidence=decision.confidence,
                raw_caption_text=caption,
                printed_figure_number=(
                    extract_printed_figure_number(
                        caption,
                        maximum_characters=(
                            maximum_printed_number_characters
                        ),
                    )
                ),
                encoded_bytes=len(encoded),
            ))

        return tuple(artifacts)

    except BaseException:
        for artifact in artifacts:
            artifact.path.unlink(missing_ok=True)
        raise


def _add_margin(
        bounds: PixelBounds,
        page_width: int,
        page_height: int,
        margin_ratio: float,
) -> PixelBounds:
    margin = round(min(page_width, page_height) * margin_ratio)

    left = max(0, bounds.left - margin)
    top = max(0, bounds.top - margin)
    right = min(page_width, bounds.right + margin)
    bottom = min(page_height, bounds.bottom + margin)

    return PixelBounds(
        left=left,
        top=top,
        width=right - left,
        height=bottom - top,
    )


def _encode_bounded_jpeg(
        crop,
        *,
        initial_quality: int,
        maximum_bytes: int,
) -> bytes:
    for quality in range(initial_quality, 74, -5):
        success, encoded = cv2.imencode(
            ".jpg",
            crop,
            [cv2.IMWRITE_JPEG_QUALITY, quality],
        )

        if not success:
            raise FigureCropError(
                "Figure crop encoding failed"
            )

        result = encoded.tobytes()
        if len(result) <= maximum_bytes:
            return result

    raise FigureCropError(
        "Figure crop exceeds the maximum encoded size"
    )


def _bounded_caption(
        value: str | None,
        maximum_characters: int,
) -> str | None:
    if value is None:
        return None

    result = value.strip()
    if not result:
        return None

    return result[:maximum_characters]


def _validate_limits(
        *,
        margin_ratio: float,
        jpeg_quality: int,
        maximum_crop_bytes: int,
        maximum_caption_characters: int,
        maximum_printed_number_characters: int,
) -> None:
    if not 0.0 <= margin_ratio <= 0.10:
        raise ValueError("Figure crop margin ratio must be between 0 and 0.10")

    if not 75 <= jpeg_quality <= 100:
        raise ValueError("Figure JPEG quality must be between 75 and 100")

    if maximum_crop_bytes <= 0:
        raise ValueError("Maximum figure crop size must be positive")

    if maximum_caption_characters <= 0:
        raise ValueError("Maximum caption length must be positive")

    if maximum_printed_number_characters <= 0:
        raise ValueError("Maximum printed number length must be positive")
