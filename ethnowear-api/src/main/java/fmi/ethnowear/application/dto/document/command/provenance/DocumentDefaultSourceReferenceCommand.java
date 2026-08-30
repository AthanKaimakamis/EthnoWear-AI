package fmi.ethnowear.application.dto.document.command.provenance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record DocumentDefaultSourceReferenceCommand(
        @NotNull @Positive Long sourceReferenceId,
        boolean inheritToUnassignedPages,
        @NotBlank @Size(max = 1000) String reason
) {
}
