package fmi.ethnowear.application.dto.retrieval;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GroundedRetrievalQuery(
        @NotBlank
        @Size(max = 1000)
        String question,

        @Min(1)
        @Max(10)
        Integer resultCount
) {
}