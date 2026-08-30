from dataclasses import dataclass
from pathlib import Path
import warnings

from PIL import Image, UnidentifiedImageError


class FigureImageError(ValueError):
    pass


@dataclass(frozen=True, slots=True)
class FigureImageInfo:
    format: str
    width: int
    height: int
    mode: str


def inspect_figure_image(
    path: Path,
    *,
    maximum_width: int,
    maximum_height: int,
    maximum_pixels: int,
) -> FigureImageInfo:
    if (
        maximum_width <= 0
        or maximum_height <= 0
        or maximum_pixels <= 0
    ):
        raise ValueError("Figure image limits must be positive")

    try:
        with warnings.catch_warnings(
            action="error",
            category=Image.DecompressionBombWarning,
        ):
            with Image.open(path) as image:
                image_format = (image.format or "").upper()
                width, height = image.size
                frame_count = getattr(image, "n_frames", 1)

                if image_format not in {"PNG", "JPEG", "TIFF"}:
                    raise FigureImageError("Figure input has an unsupported image format")

                if frame_count != 1:
                    raise FigureImageError("Figure input must contain exactly one image")

                if width <= 0 or height <= 0:
                    raise FigureImageError("Figure input has invalid dimensions")

                if width > maximum_width or height > maximum_height:
                    raise FigureImageError("Figure input dimensions exceed the maximum")

                if width * height > maximum_pixels:
                    raise FigureImageError("Figure input pixel count exceeds the maximum")

                result = FigureImageInfo(
                    format=image_format,
                    width=width,
                    height=height,
                    mode=image.mode,
                )

                image.verify()
                return result

    except FigureImageError:
        raise
    except (
        Image.DecompressionBombError,
        Image.DecompressionBombWarning,
        OSError,
        UnidentifiedImageError,
    ) as error:
        raise FigureImageError("Figure input is not a valid image") from error