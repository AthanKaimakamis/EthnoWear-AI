package fmi.ethnowear.application.dto.document.command.upload;

import fmi.ethnowear.domain.model.document.DocumentType;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceStatus;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceTrustState;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PdfDocumentUploadCommand(
        @Valid @NotNull DocumentBibliographicInput metadata,
        @NotNull DocumentType documentType,
        @NotNull ProvenanceStatus provenanceStatus,
        @NotNull ProvenanceTrustState provenanceTrustState,
        @Size(max = 2000) String mediaDescription
) {
}
