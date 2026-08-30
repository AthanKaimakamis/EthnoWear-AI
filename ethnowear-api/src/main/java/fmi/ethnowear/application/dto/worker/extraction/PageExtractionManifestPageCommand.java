package fmi.ethnowear.application.dto.worker.extraction;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record PageExtractionManifestPageCommand(
        @NotNull
        @PositiveOrZero
        Integer pdfPageIndex,

        @NotNull
        @Positive
        Integer pageSequence
) {
}