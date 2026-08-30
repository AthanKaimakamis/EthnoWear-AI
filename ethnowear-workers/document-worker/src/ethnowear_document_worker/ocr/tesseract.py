from pathlib import Path

from ethnowear_document_worker.ocr.image import detect_rotation
from ethnowear_document_worker.ocr.models import OcrOutput
from ethnowear_document_worker.ocr.process import (
    OcrProcessError,
    run_bounded_process
)
from ethnowear_document_worker.ocr.tsv import parse_tsv_metadata


class TesseractRunner:
    def __init__(
            self,
            *,
            binary: str,
            language: str,
            oem: int,
            psm: int,
            timeout_seconds: int
    ) -> None:
        self._binary = binary
        self._language = language
        self._oem = oem
        self._psm = psm
        self._timeout_seconds = timeout_seconds
        self._engine_version: str | None = None

    async def detect_rotation(self, image_path: Path) -> int:
        return await detect_rotation(
            image_path,
            binary=self._binary,
            timeout_seconds=self._timeout_seconds,
        )

    async def validate_runtime(self) -> str:
        version = await self.engine_version()
        output = await run_bounded_process(
            (self._binary, "--list-langs"),
            timeout_seconds=min(self._timeout_seconds, 10),
            maximum_stdout_bytes=16_384,
        )
        available_languages = {
            line.strip()
            for line in output.splitlines()[1:]
            if line.strip()
        }
        required_languages = set(self._language.split("+")) | {"osd"}

        if not required_languages.issubset(available_languages):
            raise OcrProcessError("OCR engine language data is unavailable")

        return version

    async def recognize(
            self,
            image_path: Path,
            maximum_output_bytes: int,
            *,
            psm: int | None = None,
            offset_x: int = 0,
            offset_y: int = 0,
    ) -> OcrOutput:
        selected_psm = self._psm if psm is None else psm
        raw_text = await run_bounded_process(
            self._command(image_path, selected_psm, "txt"),
            timeout_seconds=self._timeout_seconds,
            maximum_stdout_bytes=maximum_output_bytes,
        )
        raw_text = validate_plain_text(raw_text)
        tsv = await run_bounded_process(
            self._command(image_path, selected_psm, "tsv"),
            timeout_seconds=self._timeout_seconds,
            maximum_stdout_bytes=maximum_output_bytes,
        )
        mean_confidence, words = parse_tsv_metadata(tsv)
        if offset_x or offset_y:
            words = tuple(word.with_offset(offset_x, offset_y) for word in words)

        return OcrOutput(
            raw_text=raw_text,
            mean_confidence=mean_confidence,
            words=words,
            engine_name="tesseract",
            engine_version=await self.engine_version(),
            language=self._language,
            psm=selected_psm,
        )

    def _command(self, image_path: Path, psm: int, output_format: str) -> tuple[str, ...]:
        if psm not in {3, 4, 6}:
            raise ValueError("OCR page segmentation mode is unsupported")
        return (
            self._binary,
            str(image_path),
            "stdout",
            "-l",
            self._language,
            "--oem",
            str(self._oem),
            "--psm",
            str(psm),
            output_format,
        )

    async def engine_version(self) -> str:
        if self._engine_version is not None:
            return self._engine_version

        output = await run_bounded_process(
            (self._binary, "--version"),
            timeout_seconds=min(self._timeout_seconds, 10),
            maximum_stdout_bytes=4096,
        )

        first_line = output.partition("\n")[0].strip()
        parts = first_line.split()

        if len(parts) < 2 or parts[0].lower() != "tesseract":
            raise OcrProcessError("OCR engine returned an invalid version")

        version = parts[1]

        if len(version) > 100:
            raise OcrProcessError("OCR engine returned an invalid version")

        self._engine_version = version
        return version

    @property
    def language(self) -> str:
        return self._language


def validate_plain_text(text: str) -> str:
    normalized = text.replace("\r\n", "\n").replace("\r", "\n").strip()
    header = "level\tpage_num\tblock_num\tpar_num\tline_num\tword_num"
    tsv_rows = sum(
        1 for line in normalized.splitlines()
        if line.count("\t") >= 10 and line.split("\t", 1)[0].isdigit()
    )
    if header in normalized or tsv_rows >= 2:
        raise OcrProcessError("OCR text output contained structured rows")
    return normalized
