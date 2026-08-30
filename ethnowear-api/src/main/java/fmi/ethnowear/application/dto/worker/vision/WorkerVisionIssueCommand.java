package fmi.ethnowear.application.dto.worker.vision;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record WorkerVisionIssueCommand(
        @JsonAlias("code") @NotBlank String issueType,
        @JsonAlias("safeMessage") @NotBlank String explanationBg,
        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        @Digits(integer = 1, fraction = 4)
        BigDecimal confidence,
        String originalText,
        @JsonAlias("contextText") String originalContext,
        String suggestedText,
        String suggestedContext,
        Integer startOffset,
        Integer endOffset,
        boolean safelyApplicable
) {

    public WorkerVisionIssueCommand(
            String code,
            String originalText,
            String suggestedText,
            String safeMessage,
            BigDecimal confidence
    ) {
        this(
                code,
                safeMessage,
                confidence,
                originalText,
                null,
                suggestedText,
                null,
                null,
                null,
                false
        );
    }
}
