from dataclasses import dataclass
from pathlib import Path

from ethnowear_figure_worker.layout.models import PixelBounds


@dataclass(frozen=True, slots=True)
class FigureCropArtifact:
    candidate_id: int
    figure_ordinal: int
    path: Path
    content_type: str
    page_bounds: PixelBounds
    crop_width: int
    crop_height: int
    candidate_confidence: float
    raw_caption_text: str | None
    printed_figure_number: str | None
    encoded_bytes: int

    def __post_init__(self) -> None:
        if self.candidate_id <= 0:
            raise ValueError("Candidate ID must be positive")

        if self.figure_ordinal <= 0:
            raise ValueError("Figure ordinal must be positive")

        if self.crop_width <= 0 or self.crop_height <= 0:
            raise ValueError("Figure crop dimensions must be positive")

        if not 0.0 <= self.candidate_confidence <= 1.0:
            raise ValueError("Candidate confidence must be between zero and one")

        if self.encoded_bytes <= 0:
            raise ValueError("Encoded crop size must be positive")
