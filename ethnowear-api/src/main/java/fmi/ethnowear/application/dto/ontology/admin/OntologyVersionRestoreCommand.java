package fmi.ethnowear.application.dto.ontology.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OntologyVersionRestoreCommand(
        @NotBlank(message = "Restoration reason is required")
        @Size(max = 500, message = "Restoration reason cannot exceed 500 characters")
        String reason
) {
    public OntologyVersionRestoreCommand {
        if (reason != null)
            reason = reason.trim();
    }
}
