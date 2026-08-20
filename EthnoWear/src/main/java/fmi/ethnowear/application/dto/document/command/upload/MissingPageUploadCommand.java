package fmi.ethnowear.application.dto.document.command.upload;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record MissingPageUploadCommand(
        @Positive int pageSequence,
        @Valid @NotNull PageProvenanceInput provenance,
        @Size(max = 50) String printedPageNumber,
        Integer printedPageSort,
        @Size(max = 100) String pageLabel,
        boolean queueOcr,
        @Size(max = 2000) String mediaDescription,
        String renditionNotes
) {
}
