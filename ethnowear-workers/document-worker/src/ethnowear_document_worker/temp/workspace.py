from dataclasses import dataclass
from pathlib import Path

from ethnowear_worker_common.temp.workspace import (
    JobWorkspace as CommonJobWorkspace,
    cleanup_stale_workspaces,
)


@dataclass(frozen=True, slots=True)
class JobWorkspace(CommonJobWorkspace):

    @property
    def input_pdf(self) -> Path:
        return self.path / "input.pdf"

    @property
    def input_image(self) -> Path:
        return self.path / "input-image"

    @property
    def preprocessed_image(self) -> Path:
        return self.path / "preprocessed.png"

    def rendition_path(
            self,
            page_id: int,
            extension: str
    ) -> Path:
       if page_id <= 0:
           raise ValueError("Page ID must be positive")

       if extension not in {"jpg", "png", "webp"}:
           raise ValueError("Unsupported rendition extension")

       return self.path / f"page-{page_id}.{extension}"
