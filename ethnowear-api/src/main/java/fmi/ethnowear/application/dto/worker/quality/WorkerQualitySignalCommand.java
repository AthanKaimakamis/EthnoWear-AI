package fmi.ethnowear.application.dto.worker.quality;

import fmi.ethnowear.domain.model.document.quality.QualitySignalSeverity;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record WorkerQualitySignalCommand(
        @NotBlank String type,
        BigDecimal decimalValue,
        String textValue,
        @NotNull QualitySignalSeverity severity,
        @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal weight,
        String safeMessage
) {
}
