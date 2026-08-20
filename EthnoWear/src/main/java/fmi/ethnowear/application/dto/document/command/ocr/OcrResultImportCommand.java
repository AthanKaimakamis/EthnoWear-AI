package fmi.ethnowear.application.dto.document.command.ocr;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record OcrResultImportCommand(
        @NotNull @Positive Long documentPageMediaId,
        @Positive Long processingJobId,
        @NotNull String rawText,
        @NotBlank @Size(max = 100) String ocrEngine,
        @Size(max = 100) String ocrEngineVersion,
        @Size(max = 20) String ocrLanguage,
        @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal ocrConfidence,
        String parametersJson,
        String structuredOutputJson
) {
}
