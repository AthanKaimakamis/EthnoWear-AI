package fmi.ethnowear.application.dto.document.command.processing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DocumentJobCancellationCommand(
        @NotBlank @Size(max = 500) String reason
) {
}
