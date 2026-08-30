package fmi.ethnowear.application.dto.document.command.provenance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CanonicalPageLinkReversalCommand(
        @NotBlank
        @Size(max = 1000)
        String reason
) {
}
