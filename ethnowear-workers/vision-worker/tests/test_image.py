from pathlib import Path

from PIL import Image

from ethnowear_vision_worker.image import optimize_for_vision


def test_resizes_large_page_and_writes_bounded_jpeg(tmp_path: Path) -> None:
    source = tmp_path / "page.png"
    destination = tmp_path / "model.jpg"
    Image.new("L", (2400, 3600), 245).save(source)

    result = optimize_for_vision(
        source,
        destination,
        maximum_edge_pixels=2048,
        jpeg_quality=90,
    )

    assert result.resized is True
    assert (result.width, result.height) == (1365, 2048)
    assert result.size_bytes == destination.stat().st_size
    with Image.open(destination) as optimized:
        assert optimized.format == "JPEG"
        assert optimized.mode == "RGB"


def test_preserves_dimensions_when_page_is_already_bounded(tmp_path: Path) -> None:
    source = tmp_path / "page.png"
    destination = tmp_path / "model.jpg"
    Image.new("RGB", (800, 1200), "white").save(source)

    result = optimize_for_vision(
        source,
        destination,
        maximum_edge_pixels=2048,
        jpeg_quality=90,
    )

    assert result.resized is False
    assert (result.width, result.height) == (800, 1200)
