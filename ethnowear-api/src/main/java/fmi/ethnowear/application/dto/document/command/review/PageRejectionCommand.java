package fmi.ethnowear.application.dto.document.command.review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PageRejectionCommand(
        @NotBlank @Size(max = 1000) String reason
) {
}
