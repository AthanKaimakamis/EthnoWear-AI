from pathlib import Path

import cv2
import numpy as np

from ethnowear_document_worker.layout.preprocessing import preprocess_if_beneficial


def test_preprocessing_keeps_clear_image_unchanged(tmp_path: Path) -> None:
    source = tmp_path / "clear.png"
    destination = tmp_path / "enhanced.png"
    image = np.full((300, 400), 255, dtype=np.uint8)
    cv2.putText(image, "TEXT", (30, 160), cv2.FONT_HERSHEY_SIMPLEX, 2, 0, 4)
    cv2.imwrite(str(source), image)

    result = preprocess_if_beneficial(source, destination)

    assert result.path == source
    assert result.steps == ()
    assert not destination.exists()


def test_preprocessing_enhances_low_contrast_image(tmp_path: Path) -> None:
    source = tmp_path / "faded.png"
    destination = tmp_path / "enhanced.png"
    image = np.full((300, 400), 180, dtype=np.uint8)
    cv2.putText(image, "TEXT", (30, 160), cv2.FONT_HERSHEY_SIMPLEX, 2, 150, 4)
    cv2.imwrite(str(source), image)

    result = preprocess_if_beneficial(source, destination)

    assert result.path == destination
    assert "clahe" in result.steps
    assert destination.exists()
