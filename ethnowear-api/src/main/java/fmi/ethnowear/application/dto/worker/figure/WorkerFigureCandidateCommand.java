package fmi.ethnowear.application.dto.worker.figure;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record WorkerFigureCandidateCommand(
        @NotNull @Positive Integer candidateOrdinal,
        @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 7)
        BigDecimal normalizedX,
        @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 7)
        BigDecimal normalizedY,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1")
        @Digits(integer = 1, fraction = 7) BigDecimal normalizedWidth,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1")
        @Digits(integer = 1, fraction = 7) BigDecimal normalizedHeight,
        @Size(max = 2000) String rawCaptionText,
        @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 4)
        BigDecimal detectionConfidence
) {
}
