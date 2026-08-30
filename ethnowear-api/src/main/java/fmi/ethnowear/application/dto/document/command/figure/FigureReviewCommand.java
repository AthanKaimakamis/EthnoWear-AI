package fmi.ethnowear.application.dto.document.command.figure;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FigureReviewCommand(
        @NotBlank @Size(max = 500) String reason
) {
}
