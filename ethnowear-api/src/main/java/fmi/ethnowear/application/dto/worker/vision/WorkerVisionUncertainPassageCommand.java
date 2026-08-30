package fmi.ethnowear.application.dto.worker.vision;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record WorkerVisionUncertainPassageCommand(
        @NotBlank String excerpt,
        @NotBlank String reason,
        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        @Digits(integer = 1, fraction = 4)
        BigDecimal confidence
) {
}
