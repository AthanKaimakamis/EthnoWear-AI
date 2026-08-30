from dataclasses import dataclass
from pathlib import Path

import cv2
import numpy as np


class ImagePreprocessingError(ValueError):
    pass


@dataclass(frozen=True, slots=True)
class PreprocessingResult:
    path: Path
    steps: tuple[str, ...]


def preprocess_if_beneficial(source: Path, destination: Path) -> PreprocessingResult:
    image = cv2.imread(str(source), cv2.IMREAD_COLOR)
    if image is None:
        raise ImagePreprocessingError("OCR image preprocessing failed")

    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    result = gray
    steps: list[str] = []

    contrast_range = int(gray.max()) - int(gray.min())
    if float(np.std(gray)) < 55.0 and contrast_range < 100:
        result = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8)).apply(result)
        steps.extend(("grayscale", "clahe"))

    skew = _estimate_skew(result)
    if 0.35 <= abs(skew) <= 5.0:
        height, width = result.shape
        matrix = cv2.getRotationMatrix2D((width / 2, height / 2), skew, 1.0)
        result = cv2.warpAffine(
            result,
            matrix,
            (width, height),
            flags=cv2.INTER_CUBIC,
            borderMode=cv2.BORDER_CONSTANT,
            borderValue=255,
        )
        if "grayscale" not in steps:
            steps.append("grayscale")
        steps.append("deskew")

    if _noise_score(result) > 0.12:
        result = cv2.fastNlMeansDenoising(result, None, 7, 7, 21)
        if "grayscale" not in steps:
            steps.append("grayscale")
        steps.append("denoise")

    if not steps:
        return PreprocessingResult(source, ())

    if not cv2.imwrite(str(destination), result):
        raise ImagePreprocessingError("OCR image preprocessing failed")
    destination.chmod(0o600)
    return PreprocessingResult(destination, tuple(steps))


def _estimate_skew(gray: np.ndarray) -> float:
    binary = cv2.threshold(
        gray,
        0,
        255,
        cv2.THRESH_BINARY_INV | cv2.THRESH_OTSU,
    )[1]
    lines = cv2.HoughLinesP(
        binary,
        1,
        np.pi / 180,
        threshold=max(80, gray.shape[1] // 10),
        minLineLength=max(80, gray.shape[1] // 5),
        maxLineGap=20,
    )
    if lines is None:
        return 0.0
    angles = []
    for x1, y1, x2, y2 in lines[:, 0]:
        angle = float(np.degrees(np.arctan2(y2 - y1, x2 - x1)))
        if abs(angle) <= 10:
            angles.append(angle)
    return float(np.median(angles)) if len(angles) >= 5 else 0.0


def _noise_score(gray: np.ndarray) -> float:
    laplacian = cv2.Laplacian(gray, cv2.CV_32F)
    isolated = np.abs(laplacian) > 80
    return float(np.mean(isolated))
