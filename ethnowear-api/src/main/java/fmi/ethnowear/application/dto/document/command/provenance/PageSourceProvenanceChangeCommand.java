package fmi.ethnowear.application.dto.document.command.provenance;

import fmi.ethnowear.domain.model.document.provenance.ProvenanceStatus;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceTrustState;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PageSourceProvenanceChangeCommand(
        @Positive Long sourceReferenceId,
        @NotNull ProvenanceStatus provenanceStatus,
        @NotNull ProvenanceTrustState provenanceTrustState,
        @Size(max = 4000) String note,
        @NotBlank @Size(max = 1000) String reason
) {
}