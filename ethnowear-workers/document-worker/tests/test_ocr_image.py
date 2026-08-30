import asyncio
from pathlib import Path

import pytest
from PIL import Image

from ethnowear_document_worker.ocr.image import (
    OcrImageError,
    apply_rotation,
    detect_rotation,
    inspect_ocr_image,
)
from ethnowear_document_worker.ocr.process import OcrProcessError


def save_image(path: Path, image_format: str = "PNG") -> None:
    Image.new("RGB", (20, 10), "white").save(path, format=image_format)


@pytest.mark.parametrize(
    ("image_format", "suffix"),
    [("PNG", ".png"), ("JPEG", ".jpg"), ("TIFF", ".tiff")],
)
def test_inspect_ocr_image_accepts_supported_single_page_formats(
    tmp_path: Path,
    image_format: str,
    suffix: str,
) -> None:
    path = tmp_path / f"page{suffix}"
    save_image(path, image_format)

    result = inspect_ocr_image(
        path,
        maximum_width=100,
        maximum_height=100,
        maximum_pixels=10_000,
    )

    assert result.format == image_format
    assert (result.width, result.height) == (20, 10)
    assert result.mode == "RGB"


def test_inspect_ocr_image_rejects_corrupt_content(tmp_path: Path) -> None:
    path = tmp_path / "page.png"
    path.write_bytes(b"not-an-image")

    with pytest.raises(OcrImageError, match="not a valid image"):
        inspect_ocr_image(
            path,
            maximum_width=100,
            maximum_height=100,
            maximum_pixels=10_000,
        )


def test_inspect_ocr_image_rejects_unsupported_format(tmp_path: Path) -> None:
    path = tmp_path / "page.bmp"
    save_image(path, "BMP")

    with pytest.raises(OcrImageError, match="unsupported image format"):
        inspect_ocr_image(
            path,
            maximum_width=100,
            maximum_height=100,
            maximum_pixels=10_000,
        )


@pytest.mark.parametrize(
    ("maximum_width", "maximum_height"),
    [(19, 10), (20, 9)],
)
def test_inspect_ocr_image_enforces_dimensions(
    tmp_path: Path,
    maximum_width: int,
    maximum_height: int,
) -> None:
    path = tmp_path / "page.png"
    save_image(path)

    with pytest.raises(OcrImageError, match="dimensions exceed"):
        inspect_ocr_image(
            path,
            maximum_width=maximum_width,
            maximum_height=maximum_height,
            maximum_pixels=10_000,
        )


def test_inspect_ocr_image_enforces_pixel_count(tmp_path: Path) -> None:
    path = tmp_path / "page.png"
    save_image(path)

    with pytest.raises(OcrImageError, match="pixel count"):
        inspect_ocr_image(
            path,
            maximum_width=100,
            maximum_height=100,
            maximum_pixels=199,
        )


def test_inspect_ocr_image_rejects_multi_frame_tiff(tmp_path: Path) -> None:
    path = tmp_path / "page.tiff"
    first = Image.new("RGB", (20, 10), "white")
    second = Image.new("RGB", (20, 10), "black")
    first.save(path, format="TIFF", save_all=True, append_images=[second])

    with pytest.raises(OcrImageError, match="exactly one image"):
        inspect_ocr_image(
            path,
            maximum_width=100,
            maximum_height=100,
            maximum_pixels=10_000,
        )


def test_inspect_ocr_image_maps_decompression_bomb_warning(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    path = tmp_path / "page.png"
    save_image(path)
    monkeypatch.setattr(Image, "MAX_IMAGE_PIXELS", 100)

    with pytest.raises(OcrImageError, match="not a valid image"):
        inspect_ocr_image(
            path,
            maximum_width=100,
            maximum_height=100,
            maximum_pixels=10_000,
        )


def test_inspect_ocr_image_rejects_invalid_limits(tmp_path: Path) -> None:
    with pytest.raises(ValueError, match="must be positive"):
        inspect_ocr_image(
            tmp_path / "unused.png",
            maximum_width=0,
            maximum_height=100,
            maximum_pixels=10_000,
        )


@pytest.mark.parametrize(
    ("output", "expected"),
    [
        ("Orientation in degrees: 270\nRotate: 90\n", 90),
        ("Rotate: 0\n", 0),
        ("unrecognized output", 0),
    ],
)
def test_detect_rotation_parses_safe_osd_output(
    monkeypatch: pytest.MonkeyPatch,
    output: str,
    expected: int,
) -> None:
    async def fake_process(*args, **kwargs) -> str:
        return output

    monkeypatch.setattr(
        "ethnowear_document_worker.ocr.image.run_bounded_process",
        fake_process,
    )

    assert asyncio.run(detect_rotation(
        Path("/temporary/page.png"),
        binary="/usr/bin/tesseract",
        timeout_seconds=120,
    )) == expected


def test_detect_rotation_uses_bounded_osd_process(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    captured: dict[str, object] = {}

    async def fake_process(command, **kwargs) -> str:
        captured["command"] = tuple(command)
        captured.update(kwargs)
        return "Rotate: 180\n"

    monkeypatch.setattr(
        "ethnowear_document_worker.ocr.image.run_bounded_process",
        fake_process,
    )

    result = asyncio.run(detect_rotation(
        Path("/temporary/page.png"),
        binary="/usr/bin/tesseract",
        timeout_seconds=120,
    ))

    assert result == 180
    assert captured == {
        "command": (
            "/usr/bin/tesseract",
            "/temporary/page.png",
            "stdout",
            "--psm",
            "0",
            "-l",
            "osd",
        ),
        "timeout_seconds": 15,
        "maximum_stdout_bytes": 4096,
    }


def test_detect_rotation_treats_osd_failure_as_no_rotation(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    async def fake_process(*args, **kwargs) -> str:
        raise OcrProcessError("sanitized failure")

    monkeypatch.setattr(
        "ethnowear_document_worker.ocr.image.run_bounded_process",
        fake_process,
    )

    assert asyncio.run(detect_rotation(
        Path("/temporary/page.png"),
        binary="tesseract",
        timeout_seconds=10,
    )) == 0


@pytest.mark.parametrize(
    ("degrees", "expected_size"),
    [(90, (10, 20)), (180, (20, 10)), (270, (10, 20))],
)
def test_apply_rotation_creates_private_png(
    tmp_path: Path,
    degrees: int,
    expected_size: tuple[int, int],
) -> None:
    source = tmp_path / "source.png"
    destination = tmp_path / "corrected.png"
    save_image(source)

    assert apply_rotation(source, destination, degrees) is True

    with Image.open(destination) as corrected:
        assert corrected.format == "PNG"
        assert corrected.size == expected_size

    assert destination.stat().st_mode & 0o777 == 0o600


def test_apply_rotation_zero_does_not_create_output(tmp_path: Path) -> None:
    destination = tmp_path / "corrected.png"

    assert apply_rotation(
        tmp_path / "unused.png",
        destination,
        0,
    ) is False
    assert not destination.exists()


def test_apply_rotation_rejects_unsafe_angle(tmp_path: Path) -> None:
    with pytest.raises(ValueError, match="0, 90, 180, or 270"):
        apply_rotation(
            tmp_path / "source.png",
            tmp_path / "corrected.png",
            45,
        )


def test_apply_rotation_sanitizes_image_errors(tmp_path: Path) -> None:
    source = tmp_path / "source.png"
    source.write_bytes(b"not an image")

    with pytest.raises(OcrImageError, match="rotation failed") as captured:
        apply_rotation(source, tmp_path / "corrected.png", 90)

    assert str(source) not in str(captured.value)
