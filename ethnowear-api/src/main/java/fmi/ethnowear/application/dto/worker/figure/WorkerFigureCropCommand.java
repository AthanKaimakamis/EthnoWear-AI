package fmi.ethnowear.application.dto.worker.figure;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record WorkerFigureCropCommand(
        @NotNull @Positive Long candidateId,
        @NotNull @Positive Integer figureOrdinal,
        @Size(max = 100) String printedFigureNumber,
        @Size(max = 2000) String rawCaptionText
) {
}
