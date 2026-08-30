from dataclasses import dataclass
from pathlib import Path
import re
import warnings

from PIL import Image, UnidentifiedImageError

from ethnowear_document_worker.ocr.process import OcrProcessError, run_bounded_process


class OcrImageError(ValueError):
    pass


@dataclass(frozen=True, slots=True)
class OcrImageInfo:
    format: str
    width: int
    height: int
    mode: str


_ROTATE_PATTERN = re.compile(
    r"^Rotate:\s*(0|90|180|270)\s*$",
    re.MULTILINE,
)


async def detect_rotation(
        path: Path,
        *,
        binary: str,
        timeout_seconds: int,
) -> int:
    try:
        output = await run_bounded_process(
            (
                binary,
                str(path),
                "stdout",
                "--psm",
                "0",
                "-l",
                "osd",
            ),
            timeout_seconds=min(timeout_seconds, 15),
            maximum_stdout_bytes=4096,
        )
    except OcrProcessError:
        return 0

    match = _ROTATE_PATTERN.search(output)

    if match is None:
        return 0

    return int(match.group(1))


def apply_rotation(
        source: Path,
        destination: Path,
        degrees_clockwise: int,
) -> bool:
    if degrees_clockwise not in {0, 90, 180, 270}:
        raise ValueError("Rotation must be 0, 90, 180, or 270 degrees")

    if degrees_clockwise == 0:
        return False

    try:
        with Image.open(source) as image:
            corrected = image.rotate(
                -degrees_clockwise,
                expand=True,
                fillcolor="white",
            )
            corrected.save(destination, format="PNG")
    except (UnidentifiedImageError, OSError) as error:
        raise OcrImageError("OCR image rotation failed") from error

    destination.chmod(0o600)
    return True


def inspect_ocr_image(
        path: Path,
        *,
        maximum_width: int,
        maximum_height: int,
        maximum_pixels: int,
) -> OcrImageInfo:
    if (
            maximum_width <= 0
            or maximum_height <= 0
            or maximum_pixels <= 0
    ):
        raise ValueError("OCR image limits must be positive")

    try:
        # noinspection PyTypeChecker
        with warnings.catch_warnings(
                action="error",
                category=Image.DecompressionBombWarning,
        ):
            with Image.open(path) as image:
                image_format = (image.format or "").upper()
                width, height = image.size
                frame_count = getattr(image, "n_frames", 1)

                if image_format not in {"PNG", "JPEG", "TIFF"}:
                    raise OcrImageError("OCR input has an unsupported image format")

                if frame_count != 1:
                    raise OcrImageError("OCR input must contain exactly one image")

                if width <= 0 or height <= 0:
                    raise OcrImageError(
                        "OCR input has invalid dimensions"
                    )

                if width > maximum_width or height > maximum_height:
                    raise OcrImageError("OCR input dimensions exceed the maximum")

                if width * height > maximum_pixels:
                    raise OcrImageError("OCR input pixel count exceeds the maximum")

                result = OcrImageInfo(
                    format=image_format,
                    width=width,
                    height=height,
                    mode=image.mode,
                )

                image.verify()
                return result

    except OcrImageError:
        raise
    except (
            UnidentifiedImageError,
            OSError,
            Image.DecompressionBombError,
            Image.DecompressionBombWarning,
    ) as error:
        raise OcrImageError("OCR input is not a valid image") from error
