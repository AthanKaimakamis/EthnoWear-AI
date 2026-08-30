from dataclasses import dataclass
from enum import StrEnum

from ethnowear_figure_worker.layout.models import PixelBounds


class CandidateRejectionReason(StrEnum):
    INVALID_BOUNDS = "INVALID_BOUNDS"
    AREA_TOO_SMALL = "AREA_TOO_SMALL"
    PAGE_BORDER = "PAGE_BORDER"
    DECORATIVE_SEPARATOR = "DECORATIVE_SEPARATOR"
    TEXT_DOMINATED = "TEXT_DOMINATED"
    LOW_VISUAL_CONTENT = "LOW_VISUAL_CONTENT"


@dataclass(frozen=True, slots=True)
class CandidateDecision:
    candidate_id: int
    candidate_ordinal: int
    bounds: PixelBounds | None
    accepted: bool
    confidence: float
    rejection_reason: CandidateRejectionReason | None
    raw_caption_text: str | None

    def __post_init__(self) -> None:
        if self.candidate_id <= 0:
            raise ValueError("Candidate ID must be positive")

        if self.candidate_ordinal <= 0:
            raise ValueError("Candidate ordinal must be positive")

        if not 0.0 <= self.confidence <= 1.0:
            raise ValueError("Candidate confidence must be between zero and one")

        if self.accepted and self.bounds is None:
            raise ValueError("Accepted candidate must have bounds")

        if self.accepted and self.rejection_reason is not None:
            raise ValueError("Accepted candidate cannot have a rejection reason")

        if not self.accepted and self.rejection_reason is None:
            raise ValueError("Rejected candidate must have a rejection reason")