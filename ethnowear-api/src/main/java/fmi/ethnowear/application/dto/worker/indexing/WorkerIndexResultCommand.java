package fmi.ethnowear.application.dto.worker.indexing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record WorkerIndexResultCommand(
        @NotBlank
        @Pattern(regexp = "[0-9a-f]{64}")
        String contentHash,

        @NotBlank
        @Size(max = 100)
        String embeddingModel,

        @Positive
        int embeddingDimensions,

        @NotBlank
        @Size(max = 150)
        String vectorCollection,

        @NotBlank
        @Size(max = 255)
        String vectorPointId
) {
}
