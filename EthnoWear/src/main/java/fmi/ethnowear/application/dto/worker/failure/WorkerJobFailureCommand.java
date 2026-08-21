package fmi.ethnowear.application.dto.worker.failure;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(example = """
        {
          "errorCode": "PDF_RENDER_FAILED",
          "safeErrorMessage": "The PDF page could not be rendered",
          "retryable": true
        }
        """)
public record WorkerJobFailureCommand(
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "[A-Z][A-Z0-9_]*")
        String errorCode,

        @NotBlank
        @Size(max = 1000)
        String safeErrorMessage,

        boolean retryable
) {
}