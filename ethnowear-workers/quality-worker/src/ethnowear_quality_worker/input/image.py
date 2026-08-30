from dataclasses import dataclass
from pathlib import Path
import warnings

from PIL import Image, UnidentifiedImageError

class OcrImageError(ValueError):
    pass


@dataclass(frozen=True, slots=True)
class OcrImageInfo:
    format: str
    width: int
    height: int
    mode: str


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
