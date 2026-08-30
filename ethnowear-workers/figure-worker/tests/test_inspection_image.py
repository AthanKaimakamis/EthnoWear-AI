from pathlib import Path

from PIL import Image
import pytest

from ethnowear_figure_worker.inspection.image import (
    FigureImageError,
    inspect_figure_image,
)


def save_image(path: Path, *, width: int = 120, height: int = 80) -> None:
    Image.new("RGB", (width, height), "white").save(path, format="PNG")


def test_inspects_supported_single_frame_image(tmp_path: Path) -> None:
    path = tmp_path / "page.png"
    save_image(path)

    result = inspect_figure_image(
        path,
        maximum_width=120,
        maximum_height=80,
        maximum_pixels=9_600,
    )

    assert result.format == "PNG"
    assert result.width == 120
    assert result.height == 80
    assert result.mode == "RGB"


@pytest.mark.parametrize(
    ("maximum_width", "maximum_height", "maximum_pixels"),
    [
        (0, 80, 9_600),
        (120, 0, 9_600),
        (120, 80, 0),
    ],
)
def test_rejects_non_positive_limits(
    tmp_path: Path,
    maximum_width: int,
    maximum_height: int,
    maximum_pixels: int,
) -> None:
    path = tmp_path / "page.png"
    save_image(path)

    with pytest.raises(ValueError, match="limits must be positive"):
        inspect_figure_image(
            path,
            maximum_width=maximum_width,
            maximum_height=maximum_height,
            maximum_pixels=maximum_pixels,
        )


@pytest.mark.parametrize(
    ("maximum_width", "maximum_height", "maximum_pixels", "message"),
    [
        (119, 80, 9_600, "dimensions exceed"),
        (120, 79, 9_600, "dimensions exceed"),
        (120, 80, 9_599, "pixel count exceeds"),
    ],
)
def test_enforces_backend_image_limits(
    tmp_path: Path,
    maximum_width: int,
    maximum_height: int,
    maximum_pixels: int,
    message: str,
) -> None:
    path = tmp_path / "page.png"
    save_image(path)

    with pytest.raises(FigureImageError, match=message):
        inspect_figure_image(
            path,
            maximum_width=maximum_width,
            maximum_height=maximum_height,
            maximum_pixels=maximum_pixels,
        )


def test_rejects_invalid_image_without_exposing_content(tmp_path: Path) -> None:
    path = tmp_path / "page.png"
    path.write_bytes(b"not an image")

    with pytest.raises(FigureImageError, match="not a valid image"):
        inspect_figure_image(
            path,
            maximum_width=120,
            maximum_height=80,
            maximum_pixels=9_600,
        )
