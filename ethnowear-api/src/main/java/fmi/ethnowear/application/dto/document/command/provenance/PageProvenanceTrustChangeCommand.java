package fmi.ethnowear.application.dto.document.command.provenance;

import fmi.ethnowear.domain.model.document.provenance.ProvenanceTrustState;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PageProvenanceTrustChangeCommand(
        @NotNull ProvenanceTrustState provenanceTrustState,
        @NotBlank @Size(max = 1000) String reason
) {
}