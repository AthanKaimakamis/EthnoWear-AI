package fmi.ethnowear.application.dto.document.command.provenance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CanonicalPageLinkCommand(
        @NotNull @Positive Long canonicalDocumentPageId,
        @NotBlank @Size(max = 1000) String reason
) {
}
