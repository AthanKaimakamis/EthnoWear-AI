package fmi.ethnowear.application.dto.worker.ocr;

import fmi.ethnowear.application.model.document.ocr.OcrResultPayload;
import fmi.ethnowear.application.dto.worker.figure.WorkerFigureCandidateCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record WorkerOcrResultCommand(
        @NotNull String rawText,
        @NotBlank @Size(max = 100) String ocrEngine,
        @Size(max = 100) String ocrEngineVersion,
        @Size(max = 20) String ocrLanguage,
        @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal ocrConfidence,
        String parametersJson,
        String structuredOutputJson,
        @Valid List<WorkerFigureCandidateCommand> figureCandidates
) implements OcrResultPayload {

    public WorkerOcrResultCommand {
        figureCandidates = figureCandidates == null
                ? List.of()
                : List.copyOf(figureCandidates);
    }

    public WorkerOcrResultCommand(
            String rawText,
            String ocrEngine,
            String ocrEngineVersion,
            String ocrLanguage,
            BigDecimal ocrConfidence,
            String parametersJson,
            String structuredOutputJson
    ) {
        this(
                rawText,
                ocrEngine,
                ocrEngineVersion,
                ocrLanguage,
                ocrConfidence,
                parametersJson,
                structuredOutputJson,
                List.of()
        );
    }
}
