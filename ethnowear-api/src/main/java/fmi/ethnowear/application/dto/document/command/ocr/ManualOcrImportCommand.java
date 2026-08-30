package fmi.ethnowear.application.dto.document.command.ocr;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ManualOcrImportCommand(
        @NotNull @Positive Long documentPageMediaId,
        @NotNull String rawText
) {
}