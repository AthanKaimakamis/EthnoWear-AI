from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageOps


@dataclass(frozen=True, slots=True)
class OptimizedImage:
    width: int
    height: int
    size_bytes: int
    resized: bool


def optimize_for_vision(
    source: Path,
    destination: Path,
    *,
    maximum_edge_pixels: int,
    jpeg_quality: int,
) -> OptimizedImage:
    """Create a bounded, orientation-correct model input without changing evidence."""
    with Image.open(source) as opened:
        image = ImageOps.exif_transpose(opened)
        original_size = image.size
        image.thumbnail(
            (maximum_edge_pixels, maximum_edge_pixels),
            Image.Resampling.LANCZOS,
            reducing_gap=3.0,
        )
        if image.mode != "RGB":
            image = image.convert("RGB")
        image.save(
            destination,
            format="JPEG",
            quality=jpeg_quality,
            optimize=True,
            progressive=True,
        )

    return OptimizedImage(
        width=image.width,
        height=image.height,
        size_bytes=destination.stat().st_size,
        resized=image.size != original_size,
    )
