package fmi.ethnowear.application.dto.worker.quality;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record WorkerQualityAssessmentCommand(
        @NotBlank String assessorName,
        @NotBlank String assessorVersion,
        @NotBlank String scoreVersion,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal overallScore,
        @NotNull WorkerQualityStatus qualityStatus,
        String summary,
        String limitations,
        @NotEmpty List<@Valid WorkerQualitySignalCommand> signals
) {

    public WorkerQualityAssessmentCommand {
        if (signals != null)
            signals = List.copyOf(signals);
    }
}
